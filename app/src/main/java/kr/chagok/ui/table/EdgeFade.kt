package kr.chagok.ui.table

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val EDGE_FADE = 56.dp

/**
 * 옆으로 더 있으면 그쪽 가장자리를 흐리게 한다. 스크롤바 대신 쓴다.
 * `horizontalScroll` 보다 앞에 둬야 넘기는 내용이 아니라 보이는 창 기준으로 그린다.
 * 흐림은 내용의 알파를 깎는 것이라(DstIn) 따로 그린 층(Offscreen)이 있어야 바탕까지 뚫리지 않는다.
 */
internal fun Modifier.horizontalEdgeFade(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = EDGE_FADE.toPx()
        if (scroll.canScrollBackward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (scroll.canScrollForward) {
            val start = size.width - fade
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Black, Color.Transparent),
                    startX = start,
                    endX = size.width,
                ),
                topLeft = Offset(start, 0f),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }
