package com.logiclabs.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Master gate for decorative light effects — LED auras, seven-segment bloom, light
 * leak — as opposed to state (a lit LED stays lit, it just stops spilling).
 *
 * Backed by the Settings screen's "Bloom effects" preference and provided once from
 * the activity, so every renderer below the composition reads the same value with no
 * parameter threading. `staticCompositionLocalOf` because the value flips rarely and
 * whole-subtree invalidation on a flip is exactly what is wanted.
 *
 * Default `true`: previews, tests and any host that never provides it render the full
 * bench.
 */
val LocalRenderEffects = staticCompositionLocalOf { true }
