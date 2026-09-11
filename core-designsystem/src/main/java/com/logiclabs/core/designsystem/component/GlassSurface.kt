package com.logiclabs.core.designsystem.component

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.GlassHairline
import com.logiclabs.core.designsystem.theme.GlassSpecular
import com.logiclabs.core.designsystem.theme.GlassSurfaceColor

/**
 * Resolved once per process, not per frame. Reading [Build.VERSION.SDK_INT] inside a
 * draw or layout lambda would put a field read on the hot path for no benefit — the
 * value cannot change while the app is running.
 */
private val supportsBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val SpecularGradient = Brush.verticalGradient(
    colors = listOf(GlassSpecular, Color.Transparent)
)

/**
 * Frosted panel used for floating HUD chrome (command bar, context ribbon, sheets).
 *
 * ## Honest note on the blur
 * Compose has **no backdrop filter**. `Modifier.graphicsLayer { renderEffect = … }`
 * blurs the layer's *own* content — it cannot sample the pixels painted behind it. So
 * a "real glass" implementation that only sets a RenderEffect would produce a blurred
 * *panel*, not a blurred *background*, and would smear the child content into mush.
 *
 * What this composable actually does, in both code paths:
 * 1. a translucent [GlassSurfaceColor] fill,
 * 2. a top-to-bottom [GlassSpecular] sheen suggesting a light source above,
 * 3. a 1px [GlassHairline] edge so the panel has a defined boundary.
 *
 * On API 31+ the blur is applied to a *separate backdrop layer* that carries only the
 * fill and sheen — never the children — so it softens the panel's own gradient into a
 * diffuse glow while text stays sharp. On API 26–30 that layer is drawn unblurred; the
 * layered translucency alone reads convincingly enough at these alpha values.
 *
 * If a true backdrop blur is ever needed, it has to be done by capturing the content
 * behind into a bitmap and blurring that — well outside a design-system primitive.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.RadiusLg),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(GlassSurfaceColor)
            .border(Dimens.Hairline, GlassHairline, shape)
    ) {
        // Backdrop-only layer: holds the sheen and nothing else, so blurring it can
        // never touch the children composed below.
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (supportsBlur) {
                        Modifier.graphicsLayer {
                            renderEffect = RenderEffect
                                .createBlurEffect(24f, 24f, Shader.TileMode.DECAL)
                                .asComposeRenderEffect()
                        }
                    } else {
                        Modifier
                    }
                )
                .background(SpecularGradient)
        )
        content()
    }
}

/** Height of the sheen band when a caller wants to align content beneath it. */
val GlassSpecularHeight = 24.dp
