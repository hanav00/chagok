package kr.chagok.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import kr.chagok.R

/** 듣는 동안 마이크 버튼이 깜빡인다: 1초에 한 번, 가장 흐릴 때 0.25 */
private const val LISTENING_MIN_ALPHA = 0.25f
private const val BLINK_HALF_MILLIS = 500

/**
 * 방문 목적 받아쓰기 버튼 (방금 운행 카드·운행 상세 시트). 남긴 뒤에는 남긴 말을 흐린 강조색으로 보이고,
 * 다시 누르면 새로 듣는다
 */
@Composable
fun NoteButton(note: String, isListening: Boolean, onListen: () -> Unit, modifier: Modifier = Modifier) {
    val hasNote = note.isNotEmpty()
    val text = when {
        isListening -> stringResource(R.string.recent_listening)
        hasNote -> stringResource(R.string.recent_note_saved, note)
        else -> stringResource(R.string.recent_mic)
    }
    ActionButton(
        text = text,
        onClick = onListen,
        modifier = if (isListening) modifier.blinking() else modifier,
        icon = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
        style = if (hasNote) ButtonStyle.Done else ButtonStyle.Primary,
    )
}

private fun Modifier.blinking(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "listening")
    val blinkAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = LISTENING_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(BLINK_HALF_MILLIS), RepeatMode.Reverse),
        label = "blink",
    )
    graphicsLayer { alpha = blinkAlpha }
}
