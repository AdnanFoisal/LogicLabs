package com.logiclabs.core.designsystem.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Motion vocabulary for the bench.
 *
 * Hardware does not ease symmetrically. A switch snaps over centre and stops dead; an
 * LED filament reaches full brightness almost instantly but decays with a visible
 * afterglow. The specs here encode those asymmetries once so every component on the
 * chassis moves like it belongs to the same machine.
 */
object Motion {

    // -----------------------------------------------------------------------
    // Durations (ms) — exposed for callers that need raw numbers, e.g. delays
    // -----------------------------------------------------------------------
    const val DurationSwitchTravel: Int = 120
    const val DurationLedRise: Int = 40
    const val DurationLedDecay: Int = 90
    const val DurationSheetSlide: Int = 320
    const val DurationVoltageFade: Int = 400
    const val DurationBloomPulse: Int = 1100

    /**
     * Button/keycap depression. Slightly under-damped so the release overshoots by a
     * hair — that tiny bounce is what makes a press feel mechanical rather than
     * animated.
     */
    val TactilePress: AnimationSpec<Float> = spring(
        dampingRatio = 0.7f,
        stiffness = 900f
    )

    /** Lever crossing over centre: fast out of the gate, dead stop at the end. */
    val SwitchTravel: AnimationSpec<Float> = tween(
        durationMillis = DurationSwitchTravel,
        easing = EaseOutQuint
    )

    /** LED turn-on. Effectively instant and linear — a filament has no ease-in. */
    val LedRise: AnimationSpec<Float> = tween(
        durationMillis = DurationLedRise,
        easing = LinearEasing
    )

    /** LED turn-off. Longer with a soft tail so the afterglow reads. */
    val LedDecay: AnimationSpec<Float> = tween(
        durationMillis = DurationLedDecay,
        easing = EaseOutQuad
    )

    /** Bottom sheets and drawers entering or leaving. */
    val SheetSlide: AnimationSpec<Float> = tween(
        durationMillis = DurationSheetSlide,
        easing = EaseOutQuint
    )

    /** Segmented-control pill travel, as a fraction/offset in floats. */
    val PillSlideFloat: AnimationSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = 700f
    )

    /** Same feel, typed for [Dp] animations. */
    val PillSlideDp: AnimationSpec<Dp> = spring(
        dampingRatio = 0.8f,
        stiffness = 700f,
        visibilityThreshold = Dp.VisibilityThreshold
    )

    /** Alias for the common float case, so call sites can just say `Motion.PillSlide`. */
    val PillSlide: AnimationSpec<Float> = PillSlideFloat

    /** VCC pill crossfading between phosphor-live and dead-rail red. */
    val VoltageFade: AnimationSpec<Color> = tween(
        durationMillis = DurationVoltageFade
    )

    /** Generic float flavour of [VoltageFade] for alpha/scale that fades alongside it. */
    val VoltageFadeFloat: AnimationSpec<Float> = tween(
        durationMillis = DurationVoltageFade
    )

    /**
     * One half-cycle of the slow "pending" bloom. Drive it with an
     * `infiniteRepeatable(animation = Motion.BloomPulse, repeatMode = RepeatMode.Reverse)`
     * so the glow breathes instead of sawtoothing.
     */
    val BloomPulse: DurationBasedAnimationSpec<Float> = tween(
        durationMillis = DurationBloomPulse,
        easing = FastOutSlowInEasing
    )

    /** Spring used for anything positional that has no more specific spec. */
    val Settle: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /**
     * Picks the rise or decay spec for an LED-like value.
     *
     * Pass the value being animated *towards*. Anything brighter than the current
     * reading uses [LedRise]; anything dimmer uses [LedDecay]. Call it inline in
     * `animateFloatAsState(target, animationSpec = Motion.ledSpec(target, current))`.
     */
    fun ledSpec(target: Float, current: Float): AnimationSpec<Float> =
        if (target >= current) LedRise else LedDecay

    /**
     * Boolean convenience for the common on/off LED case: `true` is a turn-on.
     */
    fun ledSpec(turningOn: Boolean): AnimationSpec<Float> =
        if (turningOn) LedRise else LedDecay
}
