package kr.chagok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.ui.theme.BorderWidths
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TouchSizes

private const val LOCKED_ALPHA = 0.5f

private val SWITCH_WIDTH = 64.dp
private val SWITCH_HEIGHT = 36.dp
private val SWITCH_THUMB = 28.dp
private const val SWITCH_OFF_TRACK_ALPHA = 0.16f

/**
 * 세그먼트: 고른 칸만 밝게. 내보내기 종류·절세 계산 보유 형태·설정 기본 분류·누가 쓰는 차.
 * [enabled] 가 false 면 흐리게 보이고 누를 수 없다 (관리자만 바꾸는 칸).
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else LOCKED_ALPHA)
            .background(ChagokColors.section, Shapes.card)
            .padding(Spacing.groupInset),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(TouchSizes.segment)
                    .background(if (isSelected) ChagokColors.cardStrong else ChagokColors.section, Shapes.control)
                    .semantics { this.selected = isSelected }
                    .pressable(enabled = enabled && !isSelected, role = Role.Tab) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) ChagokColors.text else ChagokColors.textSub,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun ToggleSwitch(isOn: Boolean, onToggle: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val state = stringResource(if (isOn) R.string.switch_on else R.string.switch_off)
    Box(
        modifier = modifier
            .width(SWITCH_WIDTH)
            .height(SWITCH_HEIGHT)
            .background(
                if (isOn) ChagokColors.accent else ChagokColors.text.copy(alpha = SWITCH_OFF_TRACK_ALPHA),
                Shapes.pill,
            )
            .semantics {
                contentDescription = label
                stateDescription = state
            }
            .pressable(role = Role.Switch, onClick = onToggle)
            .padding(Spacing.xxs),
        contentAlignment = if (isOn) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        val thumbColor = if (isOn) ChagokColors.onAccent else ChagokColors.text
        Box(Modifier.size(SWITCH_THUMB).background(thumbColor, Shapes.pill))
    }
}

/**
 * 고른 칸 표시: 강조색 옅은 바탕 + 테두리. 고르지 않은 칸은 [idleBackground] 만 깐다.
 * 다음 출발 토글·누가 타나요·첫 실행 선택지가 같은 모양을 쓴다.
 */
fun Modifier.selectedFrame(isSelected: Boolean, shape: Shape, idleBackground: Color = Color.Transparent): Modifier =
    if (isSelected) {
        background(ChagokColors.selectedTint, shape).border(BorderWidths.selected, ChagokColors.accent, shape)
    } else {
        background(idleBackground, shape)
    }
