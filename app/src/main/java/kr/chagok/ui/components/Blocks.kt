package kr.chagok.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kr.chagok.domain.model.Category
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing

private val GAP_STROKE = 2.dp
private val GAP_STROKE_LARGE = 4.dp
private val GAP_DASH = 6.dp

/** 이보다 높은 블록만 벽돌 테두리(아래 그림자·위 빛)를 그린다. 낮은 블록에선 줄무늬처럼 보인다 */
private val EDGE_MIN_HEIGHT = 24.dp
private val EDGE_SHADE = 8.dp
private val EDGE_LIGHT = 3.dp
private const val EDGE_SHADE_ALPHA = 0.16f
private const val EDGE_LIGHT_ALPHA = 0.28f

/**
 * 운행 하나를 나타내는 블록. 색은 분류 색이고, 분류가 바뀌면 색이 천천히 바뀐다.
 * 아직 고르지 않은 기록 안 된 주행은 비어 있는 점선 칸으로 그려 "여기가 빠졌다"가 보이게 한다.
 * [content] 는 블록 안 글자 (큰 블록만)
 */
@Composable
fun TripBlock(
    category: Category?,
    isGap: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.block,
    content: @Composable RowScope.() -> Unit = {},
) {
    val color by animateColorAsState(category.color, label = "block")
    val isEmpty = isGap && category == null
    Row(
        modifier = if (isEmpty) {
            modifier.dashedOutline(color, shape)
        } else {
            modifier.clip(shape).background(color).brickEdges()
        },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 비어 있는 자리: 점선 테두리만 */
fun Modifier.dashedOutline(color: Color, shape: Shape): Modifier = drawBehind {
    val stroke = (if (size.height < EDGE_MIN_HEIGHT.toPx()) GAP_STROKE else GAP_STROKE_LARGE).toPx()
    val inset = stroke / 2
    val corner = (shape as? RoundedCornerShape)?.topStart?.toPx(size, this) ?: 0f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(corner),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(GAP_DASH.toPx(), GAP_DASH.toPx())),
        ),
    )
}

private fun Modifier.brickEdges(): Modifier = drawBehind {
    if (size.height < EDGE_MIN_HEIGHT.toPx()) return@drawBehind
    val shade = EDGE_SHADE.toPx()
    drawRect(
        ChagokColors.onBright.copy(alpha = EDGE_SHADE_ALPHA),
        topLeft = Offset(0f, size.height - shade),
        size = Size(size.width, shade),
    )
    drawRect(ChagokColors.white.copy(alpha = EDGE_LIGHT_ALPHA), size = Size(size.width, EDGE_LIGHT.toPx()))
}
