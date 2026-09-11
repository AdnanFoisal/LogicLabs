package com.logiclabs.feature.instruments.ui.hardware

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Minimal panel haptics for `:feature-instruments`.
 *
 * `:feature-instruments` deliberately does **not** depend on `:feature-tools`, so
 * this is a local, intentionally tiny helper rather than a shared abstraction.
 *
 * Fixes the previous `triggerSwitchHaptic` behaviour, which fired **two**
 * overlapping effects per tap (a 45 ms `createOneShot` *and* a
 * `performHapticFeedback` call carrying `FLAG_IGNORE_GLOBAL_SETTING or
 * FLAG_IGNORE_VIEW_SETTING`, overriding the user's system preference). Rules here:
 *
 * - exactly **one** effect per event,
 * - the system touch-feedback setting is honoured, never overridden,
 * - no ignore flags.
 */
object PanelHaptics {

    /**
     * App-level haptics gate, synchronized from SettingsRepository.
     */
    @Volatile
    var enabled: Boolean = true

    /** Light detent for data switches, rockers and tie-point contact. */
    fun tick(context: Context, view: View? = null) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (predefined(context, VibrationEffect.EFFECT_CLICK)) return
            if (predefined(context, VibrationEffect.EFFECT_TICK)) return
        }
        if (view != null && view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )) return
        oneShot(context, 18L)
    }

    /** Heavier thunk for master power — a real IDL-800A rocker is a chunky part. */
    fun heavy(context: Context, view: View? = null) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (predefined(context, VibrationEffect.EFFECT_HEAVY_CLICK)) return
        }
        if (view != null && view.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )) return
        oneShot(context, 26L)
    }

    /** Double-tap pattern for a completed / verified action. */
    fun success(context: Context, view: View? = null) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (predefined(context, VibrationEffect.EFFECT_DOUBLE_CLICK)) return
        }
        if (view != null && view.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )) return
        oneShot(context, 20L)
    }

    private fun vibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }?.takeIf { it.hasVibrator() }
    } catch (_: Exception) {
        null
    }

    /** API 29+ only: canned system effect. Returns true when it actually played. */
    private fun predefined(context: Context, effectId: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val vib = vibrator(context) ?: return false
        return try {
            vib.vibrate(VibrationEffect.createPredefined(effectId))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** minSdk-26 fallback: a single short one-shot, never stacked on top of another effect. */
    private fun oneShot(context: Context, ms: Long) {
        val vib = vibrator(context) ?: return
        try {
            vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
            // Vibration is a nicety; never let it break an interaction.
        }
    }
}
