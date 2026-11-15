package kr.chagok.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

private const val PRESSED_SCALE = 0.94f
private const val PRESSED_ALPHA = 0.8f
private const val PRESS_MILLIS = 120

/**
 * 누르는 것 공통 반응: 살짝 작아지고 흐려진다. 물결 효과는 쓰지 않는다.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) PRESSED_SCALE else 1f, tween(PRESS_MILLIS), label = "press")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = if (isPressed) PRESSED_ALPHA else 1f
    }.clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}
