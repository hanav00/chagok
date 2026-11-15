package kr.chagok.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val CHAR_STAGGER_MILLIS = 28
private const val CHAR_MILLIS = 420
private const val FADE_IN_MILLIS = 300
private val CHAR_RISE = 14.dp
private const val CHAR_TILT_DEGREES = 8f

/**
 * 한 줄 글자. [isErasing] 일 때 글자가 바뀌면 앞 글자가 한 자씩 흐려지며 위로 흩어진 뒤 새 글자가 나타난다
 * (개인으로 바꿔 장소를 지울 때). 그 밖에는 새 글자가 바로 나타난다.
 */
@Composable
fun ScatterText(
    text: String,
    isErasing: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(text) }
    val clock = remember { Animatable(0f) }
    val fadeIn = remember { Animatable(1f) }
    LaunchedEffect(text) {
        if (text == shown) return@LaunchedEffect
        if (isErasing) {
            val total = shown.length * CHAR_STAGGER_MILLIS + CHAR_MILLIS
            clock.snapTo(0f)
            clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
            clock.snapTo(0f)
        }
        shown = text
        fadeIn.snapTo(0f)
        fadeIn.animateTo(1f, tween(FADE_IN_MILLIS))
    }
    if (clock.value > 0f) {
        Row(modifier.clipToBounds().horizontalScroll(rememberScrollState(), enabled = false)) {
            shown.forEachIndexed { i, char ->
                val p = staggered(clock.value, i * CHAR_STAGGER_MILLIS, CHAR_MILLIS)
                val tilt = if (i % 2 == 0) -CHAR_TILT_DEGREES else CHAR_TILT_DEGREES
                Text(
                    text = char.toString(),
                    style = style,
                    color = color,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - p
                        translationY = -CHAR_RISE.toPx() * p
                        rotationZ = tilt * p
                    },
                )
            }
        }
    } else {
        Text(
            text = shown,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.graphicsLayer { alpha = fadeIn.value },
        )
    }
}
