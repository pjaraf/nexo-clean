package com.nexo.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val FocusOrange = Color(0xFFFF6A1A)

/** Recuadro visible al navegar con el mando (TV Box). Sin scale agresivo para no recortar. */
fun Modifier.tvFocus(
    shape: Shape = RoundedCornerShape(12.dp),
    borderWidth: Dp = 3.dp,
    focusedScale: Float = 1.03f
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(120),
        label = "tvFocusScale"
    )
    this
        .scale(scale)
        .border(
            width = if (focused) borderWidth else 1.5.dp,
            color = if (focused) FocusOrange else Color.Transparent,
            shape = shape
        )
        .onFocusChanged { focused = it.isFocused }
}
