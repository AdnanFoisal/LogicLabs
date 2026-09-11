package com.logiclabs.feature.breadboard.interaction

import androidx.compose.ui.geometry.Offset

data class LoupeState(
    val isVisible: Boolean = false,
    val touchPosition: Offset = Offset.Zero,
    val magnification: Float = 2.2f,
    val radiusDp: Float = 50f,
    val verticalOffsetDp: Float = 65f, // Floats above touch point to prevent finger obscuration
    val snappedSocket: Int? = null
)
