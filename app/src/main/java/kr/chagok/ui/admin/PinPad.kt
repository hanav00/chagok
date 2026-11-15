package kr.chagok.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.ChagokConfig
import kr.chagok.ui.components.pressable
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

// 숫자판: 정차 중 손가락으로 누르기 쉽게 크게
private val KEY_WIDTH = 108.dp
private val KEY_HEIGHT = 76.dp
private val KEY_GAP = Spacing.s
private const val KEY_DISABLED_ALPHA = 0.35f

// PIN 점
private val DOT_SIZE = 24.dp
private val DOT_GAP = Spacing.l
private val DOT_RING = 3.dp

// 복구키 칸
private val SLOT_WIDTH = 30.dp
private val SLOT_HEIGHT = 44.dp
private val SLOT_GAP = Spacing.xxs
private val SLOT_GROUP_GAP = Spacing.s
private val SLOT_SHAPE = RoundedCornerShape(8.dp) // 너비 30 칸에 Shapes.control(14)을 쓰면 알약처럼 보인다

/** 숫자판 배치: 1~9, 지우기, 0, 빈칸 */
private sealed interface PadKey {
    data class Digit(val digit: Char) : PadKey
    data object Delete : PadKey
    data object Empty : PadKey
}

private val PAD_ROWS: List<List<PadKey>> =
    "123456789".map { PadKey.Digit(it) }.chunked(3) + listOf(listOf(PadKey.Delete, PadKey.Digit('0'), PadKey.Empty))

/** 3×4 숫자판. PIN 을 너무 많이 틀려 [isBlocked] 면 숫자만 막는다 */
@Composable
internal fun PinPad(isBlocked: Boolean, onDigit: (Char) -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        PAD_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                row.forEach { key ->
                    when (key) {
                        is PadKey.Digit -> DigitKey(key.digit, isEnabled = !isBlocked) { onDigit(key.digit) }
                        PadKey.Delete -> DeleteKey(onDelete)
                        PadKey.Empty -> Box(Modifier.size(KEY_WIDTH, KEY_HEIGHT))
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, isEnabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(KEY_WIDTH, KEY_HEIGHT)
            .alpha(if (isEnabled) 1f else KEY_DISABLED_ALPHA)
            .background(ChagokColors.card, Shapes.control)
            .pressable(enabled = isEnabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(digit.toString(), fontSize = TextSizes.headline, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeleteKey(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(KEY_WIDTH, KEY_HEIGHT)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Backspace,
            contentDescription = stringResource(R.string.admin_delete),
            tint = ChagokColors.textSub,
        )
    }
}

/** PIN 은 점으로 가린다. 넣은 만큼 채운다 */
@Composable
internal fun PinDots(filled: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.admin_pin_progress, ChagokConfig.PIN_LENGTH, filled)
    Row(
        modifier = modifier.padding(top = Spacing.xs).clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
    ) {
        repeat(ChagokConfig.PIN_LENGTH) { index ->
            val dot = Modifier.size(DOT_SIZE)
            Box(
                if (index < filled) dot.background(ChagokColors.accent, Shapes.pill)
                else dot.border(DOT_RING, ChagokColors.textSub, Shapes.pill),
            )
        }
    }
}

/** 복구키는 넣은 숫자를 4자리씩 묶어 보여 준다 (옮겨 적는 숫자라 틀린 곳을 찾게) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecoveryKeyInput(input: String, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(SLOT_GROUP_GAP, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(SLOT_GROUP_GAP),
    ) {
        (0 until ChagokConfig.RECOVERY_KEY_LENGTH).chunked(ChagokConfig.RECOVERY_KEY_GROUP).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(SLOT_GAP)) {
                group.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(SLOT_WIDTH, SLOT_HEIGHT)
                            .background(ChagokColors.section, SLOT_SHAPE),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            input.getOrNull(index)?.toString().orEmpty(),
                            fontSize = TextSizes.subtitle,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/** "4821093755621048" → "4821 0937 5562 1048" */
internal fun groupRecoveryKey(key: String): String = key.chunked(ChagokConfig.RECOVERY_KEY_GROUP).joinToString(" ")
