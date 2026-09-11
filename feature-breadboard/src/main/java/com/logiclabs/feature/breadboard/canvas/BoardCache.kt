package com.logiclabs.feature.breadboard.canvas

import android.graphics.Picture
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.logiclabs.core.designsystem.theme.BenchPalette
import com.logiclabs.core.designsystem.theme.BenchPaletteState

/**
 * Records the static half of the faceplate into an [android.graphics.Picture] and replays it as a
 * single draw op.
 *
 * ### Why
 * The uncached faceplate issued roughly 3,700 draw ops every frame: 256 column channels × 2
 * round-rects, 1,280 tie-points × 2 circles, 352 rail holes × 2, plus the legend text. None of it
 * depends on circuit state, so all of it can be recorded once and replayed.
 *
 * ### Invalidation
 * The recording is keyed on **(LOD bucket, density, board geometry)** — never on the raw scale.
 * [bucketOf] quantises zoom to [BUCKET_STEP], so a pinch crosses a bucket boundary a handful of
 * times across the whole 0.4x–4.0x range instead of re-recording every frame. Panning never
 * invalidates: the picture covers the entire board and is replayed under the caller's transform.
 *
 * Hold one instance per canvas with `remember {}` and call [release] from `onDispose` if the
 * composable can leave the tree while the picture is large.
 */
class BoardCache {

    private var picture: Picture? = null
    private var keyBucket = Float.NaN
    private var keyDensity = 0f
    private var keyWidth = 0f
    private var keyHeight = 0f
    private var keyPalette: BenchPalette? = null

    /** Number of times a recording was made — useful in a debug overlay or a test. */
    var recordings: Int = 0
        private set

    /**
     * Replays the static faceplate, re-recording first if the LOD bucket or density changed.
     * Call inside the same `withTransform` that positions the live board.
     */
    fun drawStatic(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        density: Float,
        scale: Float
    ) {
        val bucket = bucketOf(scale)
        // The picture is recorded in absolute board coordinates from (0,0), so it has to be as
        // large as the board's far edges — not as large as boardBounds().width/height, which
        // would cut off the board by its ~10-unit left/top offset. Read as scalars so this
        // per-frame path does not allocate the Rect that boardBounds() would.
        val w = mapper.boardRight
        val h = mapper.boardBottom
        // The recording bakes the silkscreen ink colours, so a theme flip is a miss:
        // same reason as density, one re-record instead of stale light-mode text.
        val palette = BenchPaletteState.value

        val stale = picture == null ||
            bucket != keyBucket ||
            density != keyDensity ||
            w != keyWidth ||
            h != keyHeight ||
            palette !== keyPalette

        if (stale) record(mapper, density, bucket, w, h, palette)

        val pic = picture ?: return
        scope.drawIntoCanvas { it.nativeCanvas.drawPicture(pic) }
    }

    private fun record(
        mapper: BreadboardGeometryMapper,
        density: Float,
        bucket: Float,
        width: Float,
        height: Float,
        palette: BenchPalette
    ) {
        val pic = Picture()
        val nativeCanvas = pic.beginRecording(
            kotlin.math.ceil(width).toInt().coerceAtLeast(1),
            kotlin.math.ceil(height).toInt().coerceAtLeast(1)
        )
        try {
            CanvasDrawScope().draw(
                density = Density(density),
                layoutDirection = LayoutDirection.Ltr,
                canvas = Canvas(nativeCanvas),
                size = Size(width, height)
            ) {
                // The recording covers the whole board, so the "viewport" is the board itself;
                // per-frame culling is unnecessary once this collapses to one replayed op.
                FaceplateRenderer.drawStatic(
                    scope = this,
                    mapper = mapper,
                    density = density,
                    zoomScale = bucket,
                    viewport = mapper.boardBounds()
                )
            }
        } finally {
            pic.endRecording()
        }

        picture = pic
        keyBucket = bucket
        keyDensity = density
        keyWidth = width
        keyHeight = height
        keyPalette = palette
        recordings++
    }

    /** Drops the recording. Safe to call more than once. */
    fun release() {
        picture = null
        keyBucket = Float.NaN
    }

    companion object {
        /**
         * Zoom quantisation step. Coarse enough that pinching does not thrash the cache, fine
         * enough that the two LOD thresholds ([TiePointPainter.LOD_CLIP_MIN_SCALE] at 0.5 and
         * [FaceplateRenderer.LOD_SILKSCREEN_MIN_SCALE] at 0.75) each land on a bucket boundary.
         */
        const val BUCKET_STEP = 0.25f

        /**
         * Floor-quantised, so a bucket covers `[n·step, (n+1)·step)` and the LOD thresholds
         * at 0.5 / 0.75 coincide with band *starts*. The previous `round()` made each bucket
         * span ±half a step, engaging every LOD half a step early — full tie-point clips
         * (0.4–0.5px strokes) rendered below the documented 0.5, and the live highlight path
         * (which evaluates LOD at the raw scale) disagreed with the cached static layer.
         */
        fun bucketOf(scale: Float): Float =
            (kotlin.math.floor(scale / BUCKET_STEP) * BUCKET_STEP).coerceIn(BUCKET_STEP, 4.0f)
    }
}
