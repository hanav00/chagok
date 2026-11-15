package kr.chagok.ui.simulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.tax.MoneyInput
import kr.chagok.ui.components.pressable
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

// 슬라이더 모양. 손잡이를 크게 해서 차 안에서도 잡기 쉽게
private val SLIDER_HEIGHT = 56.dp
private val TRACK_HEIGHT = 10.dp
private val THUMB_SIZE = 44.dp
private val THUMB_RING = 6.dp
private val STEP_BUTTON_SIZE = 64.dp
private val STEP_ICON_SIZE = 30.dp
private val STEPPER_GAP = Spacing.s

/**
 * 금액 입력: 슬라이더와 양옆 −/+. −/+ 는 한 칸([MoneyInput.step])씩 정확히 맞출 때 쓴다.
 * [label] 은 화면 읽기용 이름이다 (−/+ 는 "월 리스료 줄이기"처럼 읽힌다).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyStepper(
    input: MoneyInput,
    value: Int,
    label: String,
    onValueChange: (Float) -> Unit,
    onStep: (direction: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = (value - input.min).toFloat() / (input.max - input.min)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(STEPPER_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            isIncrease = false,
            contentDescription = stringResource(R.string.decrease, label),
            onClick = { onStep(-1) },
        )
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .height(SLIDER_HEIGHT)
                .semantics { contentDescription = label },
            valueRange = input.min.toFloat()..input.max.toFloat(),
            // 눈금 사이 칸 수. 끄는 동안에도 한 칸 단위로 붙는다
            steps = (input.max - input.min) / input.step - 1,
            thumb = { Thumb() },
            track = { Track(fraction) },
        )
        StepButton(
            isIncrease = true,
            contentDescription = stringResource(R.string.increase, label),
            onClick = { onStep(1) },
        )
    }
}

/** 슬라이더 없이 − 값 + 만: 넓은 창 절세 계산 왼쪽 칸 */
@Composable
internal fun MoneyStepRow(label: String, value: String, onStep: (direction: Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(STEPPER_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(isIncrease = false, contentDescription = stringResource(R.string.decrease, label), onClick = { onStep(-1) })
        Text(
            value,
            Modifier.weight(1f),
            fontSize = TextSizes.title,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        StepButton(isIncrease = true, contentDescription = stringResource(R.string.increase, label), onClick = { onStep(1) })
    }
}

// 흰 손잡이에 어두운 고리를 둘러 밝은 강조색 막대와 붙어 보이지 않게 한다
@Composable
private fun Thumb() {
    Box(
        Modifier
            .size(THUMB_SIZE)
            .drawBehind {
                drawCircle(ChagokColors.window, radius = size.minDimension / 2 + THUMB_RING.toPx())
                drawCircle(ChagokColors.white)
            },
    )
}

// 지금 값까지 강조색으로 채운 막대
@Composable
private fun Track(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .background(ChagokColors.cardStrong, Shapes.pill),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(TRACK_HEIGHT)
                .background(ChagokColors.accent, Shapes.pill),
        )
    }
}

@Composable
private fun StepButton(isIncrease: Boolean, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(STEP_BUTTON_SIZE)
            .background(ChagokColors.cardStrong, Shapes.pill)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isIncrease) Icons.Rounded.Add else Icons.Rounded.Remove,
            contentDescription = contentDescription,
            tint = ChagokColors.text,
            modifier = Modifier.size(STEP_ICON_SIZE),
        )
    }
}
