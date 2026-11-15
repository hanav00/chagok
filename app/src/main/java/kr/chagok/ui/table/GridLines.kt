package kr.chagok.ui.table

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kr.chagok.ui.theme.ChagokColors

private val GRID_LINE = 1.dp
private val GRID_COLOR = ChagokColors.text.copy(alpha = 0.07f)

// 칸마다 오른쪽·아래 선만 긋고, 표 바깥 위·왼쪽 선은 줄 틀([outerGridLines])이 긋는다 (선이 겹치지 않게)
internal fun Modifier.gridLines(): Modifier = drawBehind {
    val line = GRID_LINE.toPx()
    drawRect(GRID_COLOR, topLeft = Offset(size.width - line, 0f), size = Size(line, size.height))
    drawRect(GRID_COLOR, topLeft = Offset(0f, size.height - line), size = Size(size.width, line))
}

// 표 한 줄의 바깥 왼쪽 선. 머리 줄([isTop])은 위쪽 선도
internal fun Modifier.outerGridLines(isTop: Boolean): Modifier = drawWithContent {
    drawContent()
    val line = GRID_LINE.toPx()
    if (isTop) drawRect(GRID_COLOR, size = Size(size.width, line))
    drawRect(GRID_COLOR, size = Size(line, size.height))
}
