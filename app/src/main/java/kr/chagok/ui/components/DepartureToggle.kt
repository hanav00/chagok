package kr.chagok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kr.chagok.domain.model.Category
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

/**
 * 다음 출발 토글 크기. 홈은 차 안에서 바로 누르는 큰 토글, 연습 화면은 설명 옆에 놓는 작은 토글.
 */
enum class DepartureToggleSize(
    val height: Dp,
    val icon: Dp,
    val text: TextUnit,
    val gap: Dp,
    val frameShape: RoundedCornerShape,
) {
    Large(
        height = 96.dp,
        icon = 44.dp,
        text = TextSizes.title,
        gap = Spacing.s,
        frameShape = Shapes.card,
    ),
    Compact(
        height = 64.dp,
        icon = 24.dp,
        text = TextSizes.body,
        gap = Spacing.xs,
        frameShape = Shapes.control,
    ),
}

/**
 * 다음 출발 [업무|개인] 토글. 고른 쪽이 곧 지금 상태라 상태 막대를 따로 두지 않는다.
 * 홈에서 가장 자주 누르는 것이라 고른 쪽을 강조색으로 채워 멀리서도 보이게 한다.
 * 고른 값이 출퇴근이어도 토글에는 두 칸만 있어 업무 쪽이 켜진다.
 *
 * @param onPick null 이면 보여 주기만 한다 (Gleo 연습에서 말로 바뀌는 걸 본다)
 */
@Composable
fun DepartureToggle(
    shown: Category,
    onPick: ((Category) -> Unit)?,
    modifier: Modifier = Modifier,
    size: DepartureToggleSize = DepartureToggleSize.Large,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ChagokColors.section, size.frameShape)
            .padding(Spacing.groupInset),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Category.DEPARTURE_CHOICES.forEach { choice ->
            val isOn = choice == shown || (choice == Category.BUSINESS && shown == Category.COMMUTE)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(size.height)
                    .background(if (isOn) ChagokColors.accent else Color.Transparent, Shapes.control)
                    .semantics { this.selected = isOn }
                    .then(if (onPick != null) Modifier.pressable(role = Role.Tab) { onPick(choice) } else Modifier),
                horizontalArrangement = Arrangement.spacedBy(size.gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = choice.departureIcon,
                    contentDescription = null,
                    tint = if (isOn) ChagokColors.onAccent else ChagokColors.textSub,
                    modifier = Modifier.size(size.icon),
                )
                Text(
                    text = stringResource(choice.departureLabelRes),
                    color = if (isOn) ChagokColors.onAccent else ChagokColors.textSub,
                    fontSize = size.text,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
