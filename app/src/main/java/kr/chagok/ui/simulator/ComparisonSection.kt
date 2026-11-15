package kr.chagok.ui.simulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.tax.Ownership
import kr.chagok.domain.tax.TaxComparison
import kr.chagok.domain.tax.TaxConfig
import kr.chagok.ui.components.Section
import kr.chagok.ui.components.SourceText
import kr.chagok.ui.components.pressable
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kr.chagok.ui.theme.TouchSizes

// 표 칸. 이름 칸을 조금 넓게 둬서 "사용자 상여 처리"가 한 줄에 들어가게
private const val LABEL_WEIGHT = 1.3f
private const val VALUE_WEIGHT = 1f
private val CELL_VERTICAL = Spacing.s
private val CELL_HORIZONTAL = Spacing.xs
private const val NOTE_LINE_HEIGHT = 1.5f
private const val HALF_TURN = 180f

/**
 * 기록 없음과 차곡 비교표, 접힌 용어 설명, 관련비용 한 줄.
 * 줄어드는 세금 대부분이 상여 처리에서 나와서 그 줄을 크게 보인다.
 */
@Composable
fun ComparisonSection(ownership: Ownership, comparison: TaxComparison) {
    val noLog = comparison.noLog
    val withLog = comparison.withLog
    Section(gap = 0.dp) {
        ComparisonRow(
            label = "",
            noLog = stringResource(R.string.sim_col_no_log),
            withLog = stringResource(R.string.sim_col_with_log),
            isHeader = true,
        )
        ComparisonRow(
            label = stringResource(R.string.sim_row_ratio),
            noLog = Formatters.percent(noLog.ratio),
            withLog = Formatters.percent(withLog.ratio),
        )
        ComparisonRow(
            label = stringResource(R.string.sim_row_deducted),
            noLog = Formatters.money(noLog.deductedNow),
            withLog = Formatters.money(withLog.deductedNow),
        )
        ComparisonRow(
            label = stringResource(R.string.sim_row_carry),
            noLog = Formatters.money(noLog.carryOver),
            withLog = Formatters.money(withLog.carryOver),
        )
        ComparisonRow(
            label = stringResource(R.string.sim_row_bonus),
            noLog = Formatters.money(noLog.bonus),
            withLog = Formatters.money(withLog.bonus),
            isKey = true,
        )
        TermsNote()
        SourceText(totalLine(ownership, comparison))
    }
}

/** 이월·상여 처리 뜻. 표를 읽는 데 꼭 필요하지 않아서 "용어 설명"을 눌러야 펼친다 */
@Composable
private fun TermsNote() {
    var isOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(top = Spacing.xs, bottom = Spacing.s)) {
        Row(
            modifier = Modifier
                .heightIn(min = TouchSizes.headButton)
                .pressable { isOpen = !isOpen },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.sim_terms), color = ChagokColors.textSub, fontWeight = FontWeight.Medium)
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = ChagokColors.textSub,
                modifier = Modifier.rotate(if (isOpen) HALF_TURN else 0f),
            )
        }
        if (isOpen) {
            Text(
                text = stringResource(R.string.sim_note, Formatters.money(TaxConfig.DEPRECIATION_LIMIT)),
                color = ChagokColors.textSub,
                style = LocalTextStyle.current.copy(lineHeight = TextSizes.body * NOTE_LINE_HEIGHT),
            )
        }
    }
}

// 리스·렌트의 감가상각비는 실제 상각이 아니라 요금 중 일정 비율이라 "상당액"을 붙인다
@Composable
private fun totalLine(ownership: Ownership, comparison: TaxComparison): String {
    val equivalent = if (ownership == Ownership.BUY) "" else stringResource(R.string.sim_equiv)
    return stringResource(
        R.string.sim_total,
        Formatters.money(comparison.totalCost),
        equivalent,
        Formatters.money(comparison.depreciation),
    )
}

@Composable
private fun ComparisonRow(
    label: String,
    noLog: String,
    withLog: String,
    isHeader: Boolean = false,
    isKey: Boolean = false,
) {
    val fontSize = if (isKey) TextSizes.subtitle else TextSizes.body
    val labelWeight = if (isKey) FontWeight.ExtraBold else FontWeight.Medium
    Column {
        if (!isHeader) HorizontalDivider(color = ChagokColors.divider)
        Row(Modifier.fillMaxWidth()) {
            Cell(label, LABEL_WEIGHT, ChagokColors.textSub, labelWeight, fontSize, TextAlign.Start)
            if (isHeader) {
                Cell(noLog, VALUE_WEIGHT, ChagokColors.textSub, FontWeight.Medium, fontSize)
                Cell(withLog, VALUE_WEIGHT, ChagokColors.textSub, FontWeight.Medium, fontSize)
            } else {
                // 차곡 칸은 흰색으로 띄우고 기록 없음 칸은 흐리게 둔다 (숫자에는 강조색을 쓰지 않는다)
                val noLogWeight = if (isKey) FontWeight.ExtraBold else FontWeight.Normal
                val withLogWeight = if (isKey) FontWeight.ExtraBold else FontWeight.Bold
                Cell(noLog, VALUE_WEIGHT, ChagokColors.textSub, noLogWeight, fontSize)
                Cell(withLog, VALUE_WEIGHT, ChagokColors.text, withLogWeight, fontSize)
            }
        }
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    color: Color,
    fontWeight: FontWeight,
    fontSize: TextUnit,
    textAlign: TextAlign = TextAlign.End,
) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = CELL_HORIZONTAL, vertical = CELL_VERTICAL),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
    )
}
