package kr.chagok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kr.chagok.ui.theme.TouchSizes

private const val DISABLED_ALPHA = 0.4f
private const val DONE_TINT_ALPHA = 0.14f
private val ACTION_ICON = 28.dp
private val HEADER_ICON = 26.dp

enum class ButtonStyle(val background: Color, val content: Color) {
    /** 하고 싶은 일 하나 (강조색) */
    Primary(ChagokColors.accent, ChagokColors.onAccent),

    /** 그 밖의 버튼 */
    Secondary(ChagokColors.cardStrong, ChagokColors.text),

    /** 이미 한 일 (방문 목적을 남긴 뒤의 마이크 버튼) */
    Done(ChagokColors.accent.copy(alpha = DONE_TINT_ALPHA), ChagokColors.accent),
}

/** 큰 버튼: 방금 운행 카드·내보내기 경고·복구키 확인 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ButtonStyle = ButtonStyle.Secondary,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .height(TouchSizes.button)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(style.background, Shapes.control)
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = style.content, modifier = Modifier.size(ACTION_ICON))
        }
        Text(text, color = style.content, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 화면 위쪽 줄의 작은 버튼: 기록부 내보내기·서식 전체 보기, 설정의 PIN 버튼 */
@Composable
fun HeaderButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    text: String? = null,
    contentDescription: String? = text,
    isAccent: Boolean = false,
) {
    val content = if (isAccent) ChagokColors.onAccent else ChagokColors.text
    Row(
        modifier = modifier
            .height(TouchSizes.headButton)
            .background(if (isAccent) ChagokColors.accent else ChagokColors.card, Shapes.control)
            .pressable(onClick = onClick)
            .padding(horizontal = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = if (text == null) contentDescription else null,
            tint = content,
            modifier = Modifier.size(HEADER_ICON),
        )
        if (text != null) {
            Text(text, color = content, fontWeight = if (isAccent) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
        }
    }
}

/** 상자 없는 글자 버튼 "자세히 ›": 화면에서 덜어 낸 것을 시트로 연다 */
@Composable
fun MoreButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(TouchSizes.headButton)
            .pressable(onClick = onClick)
            .padding(horizontal = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1)
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = ChagokColors.textSub,
            modifier = Modifier.size(HEADER_ICON),
        )
    }
}

/** 둥근 아이콘 버튼: 카드·시트 닫기 (흐리지 않게, 손가락 크기로) */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = ChagokColors.cardStrong,
    tint: Color = ChagokColors.textSub,
) {
    Box(
        modifier = modifier
            .size(TouchSizes.round)
            .background(background, Shapes.pill)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

/** 작은 분류 버튼: 홈 분류 대기 줄 */
@Composable
fun MiniButton(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(TouchSizes.headButton)
            .background(ChagokColors.cardStrong, Shapes.control)
            .pressable(onClick = onClick)
            .padding(horizontal = Spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = TextSizes.body, fontWeight = FontWeight.Bold)
    }
}
