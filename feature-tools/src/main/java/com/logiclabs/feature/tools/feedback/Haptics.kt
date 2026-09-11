package com.logiclabs.feature.tools.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Bench haptics for the tools and verifier surfaces.
 *
 * ## Why this exists
 * The console's `triggerSwitchHaptic` fires **two** overlapping effects for a single
 * tap — a 45 ms `createOneShot` on the vibrator *plus* a `performHapticFeedback` with
 * `FLAG_IGNORE_GLOBAL_SETTING or FLAG_IGNORE_VIEW_SETTING` — which both double-buzzes
 * and deliberately overrides the user's system haptics preference. Every entry point
 * here fires exactly **one** effect and passes **no ignore-flags**, so a user who has
 * turned haptics off gets silence.
 *
 * Prefer `performHapticFeedback` where a platform constant exists: it routes through the
 * system's haptics pipeline (which respects the user's setting and the device's tuned
 * waveforms) instead of hitting the raw vibrator. The `Vibrator` path is only a fallback
 * for tiers where the constant does not exist.
 *
 * Every call is wrapped: haptics are a garnish, and a device with no vibrator, a
 * revoked `VIBRATE` permission, or an OEM quirk must degrade silently.
 */
object Haptics {

    /**
     * App-level kill switch, driven by the Settings screen via the SettingsRepository.
     *
     * `performHapticFeedback` already honours the *system* haptics preference; this
     * covers the app-level preference, so the two compose: silence here if either the
     * user's phone or the user's bench says so.
     */
    @Volatile
    var enabled: Boolean = true

    /** Light tick: switch toggle, tie-point selection, stepper detent. */
    fun tick(view: View?) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (playPredefined(view, VibrationEffect.EFFECT_CLICK)) return
            if (playPredefined(view, VibrationEffect.EFFECT_TICK)) return
        }
        if (perform(view, HapticFeedbackConstants.CLOCK_TICK)) return
        oneShot(view, durationMs = 18L)
    }

    /** Heavy thud: master power on/off. */
    fun thud(view: View?) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (playPredefined(view, VibrationEffect.EFFECT_HEAVY_CLICK)) return
        }
        // Pre-29 has no heavy-click primitive: one short one-shot, no ignore-flags,
        // and only when the device actually has a vibrator.
        oneShot(view, durationMs = 28L)
    }

    /** Double click: a truth-table sweep that passed. */
    fun success(view: View?) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (playPredefined(view, VibrationEffect.EFFECT_DOUBLE_CLICK)) return
        }
        perform(view, HapticFeedbackConstants.LONG_PRESS)
    }

    /** Reject: invalid drop, chip burnout, failed verification. */
    fun reject(view: View?) {
        if (!enabled) return
        // REJECT landed in API 30.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (perform(view, HapticFeedbackConstants.REJECT)) return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (playPredefined(view, VibrationEffect.EFFECT_DOUBLE_CLICK)) return
        }
        oneShot(view, durationMs = 40L)
    }

    private fun perform(view: View?, constant: Int): Boolean {
        return try {
            // No FLAG_IGNORE_* — the user's system and view haptic settings win.
            view?.performHapticFeedback(constant) ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private fun playPredefined(view: View?, effectId: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val vibrator = vibrator(view?.context) ?: return false
            if (!vibrator.hasVibrator()) return false
            vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun oneShot(view: View?, durationMs: Long): Boolean {
        return try {
            val vibrator = vibrator(view?.context) ?: return false
            if (!vibrator.hasVibrator()) return false
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun vibrator(context: Context?): Vibrator? {
        if (context == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }
}
