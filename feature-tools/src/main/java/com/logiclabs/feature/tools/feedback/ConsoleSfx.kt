package com.logiclabs.feature.tools.feedback

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthesized console sound effects.
 *
 * ## Why synthesis and not assets
 * There is no `assets/` or `res/raw/` anywhere in this project and the build is offline,
 * so no audio file can be bundled. Every voice here is generated as PCM16 at
 * [SAMPLE_RATE] **once at construction** and loaded into a `MODE_STATIC` [AudioTrack],
 * which means playback is a `stop()`/`reloadStaticData()`/`play()` with no per-tap
 * allocation, no decoding, and no file I/O.
 *
 * No permission is required: `AudioTrack` playback needs none. (The app manifest's
 * `RECORD_AUDIO` is for something else entirely and nothing here depends on it.)
 *
 * Every public method swallows failures. Audio focus contention, an OEM with no usable
 * output, or a track that failed to initialise must never take the bench down.
 *
 * Construct one per screen via [rememberConsoleSfx], which also calls [release] on
 * dispose. Reach it from deeper composables through [LocalConsoleSfx].
 */
class ConsoleSfx(context: Context) {

    private val prefs: SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    /**
     * Mute state, persisted across launches. Default is **unmuted**.
     * Setting it writes through to `SharedPreferences` immediately.
     */
    var muted: Boolean = prefs?.getBoolean(KEY_MUTED, false) ?: false
        set(value) {
            field = value
            try {
                prefs?.edit()?.putBoolean(KEY_MUTED, value)?.apply()
            } catch (_: Throwable) {
            }
        }

    private val clickTrack: AudioTrack? = buildTrack(synthClick())
    private val popTrack: AudioTrack? = buildTrack(synthPop())
    private val tickTrack: AudioTrack? = buildTrack(synthTick())

    /** Toggle switch: a ~5 ms noise burst plus a 1.2 kHz exponentially decaying tone. */
    fun click() = play(clickTrack)

    /** Relay / power contactor: a ~12 ms low-frequency thud. */
    fun pop() = play(popTrack)

    /** Tie-point selection: a very short, soft tick. */
    fun tick() = play(tickTrack)

    /** Frees all three tracks. Safe to call more than once. */
    fun release() {
        releaseTrack(clickTrack)
        releaseTrack(popTrack)
        releaseTrack(tickTrack)
    }

    private fun play(track: AudioTrack?) {
        if (muted || track == null) return
        try {
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            // MODE_STATIC replays the same buffer: rewind, then go.
            track.stop()
            track.reloadStaticData()
            track.play()
        } catch (_: Throwable) {
        }
    }

    private fun releaseTrack(track: AudioTrack?) {
        try {
            track?.stop()
        } catch (_: Throwable) {
        }
        try {
            track?.release()
        } catch (_: Throwable) {
        }
    }

    private fun buildTrack(pcm: ShortArray): AudioTrack? {
        if (pcm.isEmpty()) return null
        return try {
            val bytes = pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // SONIFICATION: UI feedback, so it ducks under media rather than
                        // grabbing focus, and follows the system UI-sounds routing.
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return null
            }
            track.write(pcm, 0, pcm.size)
            track.setVolume(AudioTrack.getMaxVolume() * 0.55f)
            track
        } catch (_: Throwable) {
            null
        }
    }

    // --- Synthesis -----------------------------------------------------------
    //
    // All three voices share the same shape: a short attack, an exponential decay
    // envelope, and a 1 ms raised-cosine fade at both ends so the buffer boundary never
    // produces a DC step (which would be an audible click on top of the intended one).

    /** ~5 ms broadband noise burst layered over a 1.2 kHz decaying tone. */
    private fun synthClick(): ShortArray {
        val burst = ms(5)
        val total = ms(38)
        val out = ShortArray(total)
        val rng = Random(0x5EED)
        for (i in 0 until total) {
            val t = i.toFloat() / SAMPLE_RATE
            val toneEnv = exp(-t * 95f)
            val tone = sin(TWO_PI * 1200f * t) * toneEnv * 0.55f
            val noise = if (i < burst) {
                val noiseEnv = 1f - (i.toFloat() / burst)
                (rng.nextFloat() * 2f - 1f) * noiseEnv * 0.45f
            } else {
                0f
            }
            out[i] = pcm((tone + noise) * fade(i, total))
        }
        return out
    }

    /** ~12 ms low thud: a 90 Hz body with a 150 Hz transient on the attack. */
    private fun synthPop(): ShortArray {
        val body = ms(12)
        val total = ms(70)
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 42f)
            val low = sin(TWO_PI * 90f * t) * 0.7f
            val thump = if (i < body) sin(TWO_PI * 150f * t) * 0.35f else 0f
            out[i] = pcm((low + thump) * env * fade(i, total))
        }
        return out
    }

    /** Very short, soft tick for tie-point selection. */
    private fun synthTick(): ShortArray {
        val total = ms(16)
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 260f)
            val tone = sin(TWO_PI * 2400f * t) * 0.3f
            out[i] = pcm(tone * env * fade(i, total))
        }
        return out
    }

    private fun ms(milliseconds: Int): Int = (SAMPLE_RATE * milliseconds) / 1000

    /** 1 ms raised-cosine ramp at each end, so the buffer starts and ends at zero. */
    private fun fade(index: Int, total: Int): Float {
        val ramp = ms(1).coerceAtLeast(1)
        if (total <= ramp * 2) return 1f
        return when {
            index < ramp -> 0.5f - 0.5f * kotlin.math.cos(PI.toFloat() * index / ramp)
            index > total - ramp -> 0.5f - 0.5f * kotlin.math.cos(PI.toFloat() * (total - index) / ramp)
            else -> 1f
        }
    }

    private fun pcm(value: Float): Short {
        val clamped = value.coerceIn(-1f, 1f)
        return (clamped * 32000f).toInt().toShort()
    }

    companion object {
        /** 22050 Hz is ample for clicks and thuds and halves the buffer cost of 44.1 kHz. */
        const val SAMPLE_RATE = 22050

        private const val PREFS_NAME = "logic_labs_console_sfx"
        private const val KEY_MUTED = "muted"
        private const val TWO_PI = (2.0 * PI).toFloat()
    }
}

/**
 * A no-op fallback so `LocalConsoleSfx.current` is always safe to call, even in a
 * preview or a composable outside any provider.
 */
private val NoOpSfx: ConsoleSfx? = null

/**
 * Ambient [ConsoleSfx] for composables too deep to be passed one.
 *
 * Provide it near the root:
 * ```
 * val sfx = rememberConsoleSfx()
 * CompositionLocalProvider(LocalConsoleSfx provides sfx) { … }
 * ```
 * Reads default to null when nothing provided one, so call sites use `?.click()`.
 */
val LocalConsoleSfx: ProvidableCompositionLocal<ConsoleSfx?> = compositionLocalOf { NoOpSfx }

/**
 * Creates a [ConsoleSfx] scoped to the composition and releases its `AudioTrack`s from
 * `onDispose`. Synthesis happens once, on the first composition.
 */
@Composable
fun rememberConsoleSfx(): ConsoleSfx {
    val context = LocalContext.current
    val sfx = remember(context) { ConsoleSfx(context) }
    DisposableEffect(sfx) {
        onDispose { sfx.release() }
    }
    return sfx
}
