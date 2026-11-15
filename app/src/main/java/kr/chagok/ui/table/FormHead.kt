package kr.chagok.ui.table

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.FiscalYear
import kr.chagok.domain.model.VehicleInfo
import kr.chagok.ui.components.pressable
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val SUMMARY_MIN_HEIGHT = 60.dp
private val HEAD_PADDING_H = Spacing.l
private const val ARROW_TURN_MILLIS = 150
private const val OPEN_ARROW_DEGREES = 180f

/** 서식 머리 칸 수: 분할 칸은 두 칸씩, 넓게는 한 줄에 다섯 칸 */
private const val NARROW_COLUMNS = 2
private const val WIDE_COLUMNS = 5

/**
 * 서식 머리: 사업연도·법인명·사업자번호·차종·번호판.
 * 매번 같은 값이라 접으면 "법인명 · 차종 번호판" 한 줄로 둔다. 아직 안 넣은 값은 "입력 필요".
 */
@Composable
internal fun FormHead(
    vehicle: VehicleInfo,
    fiscalYear: FiscalYear,
    isOpen: Boolean,
    isWide: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val missing = stringResource(R.string.form_missing)
    val arrowTurn by animateFloatAsState(
        targetValue = if (isOpen) OPEN_ARROW_DEGREES else 0f,
        animationSpec = tween(ARROW_TURN_MILLIS),
        label = "formHeadArrow",
    )
    val vehicleLine = stringResource(
        R.string.table_form_vehicle,
        vehicle.model.ifBlank { missing },
        vehicle.plate.ifBlank { missing },
    )
    val summary = vehicle.company.ifBlank { missing } + Formatters.SEPARATOR + vehicleLine

    Column(modifier.fillMaxWidth().background(ChagokColors.section, Shapes.control)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = SUMMARY_MIN_HEIGHT)
                .pressable { onToggle(!isOpen) }
                .padding(horizontal = HEAD_PADDING_H),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = ChagokColors.textSub,
                modifier = Modifier.rotate(arrowTurn),
            )
        }
        if (isOpen) {
            val range = stringResource(
                R.string.form_fiscal_range,
                Formatters.periodDay(fiscalYear.start),
                Formatters.periodDay(fiscalYear.end),
            )
            FormInfoGrid(
                items = listOf(
                    R.string.form_fiscal_year to range,
                    R.string.form_company to vehicle.company,
                    R.string.form_biz_no to vehicle.bizNo,
                    R.string.form_model to vehicle.model,
                    R.string.form_plate to vehicle.plate,
                ).map { (label, value) -> label to value.ifBlank { missing } },
                columns = if (isWide) WIDE_COLUMNS else NARROW_COLUMNS,
            )
        }
    }
}

@Composable
private fun FormInfoGrid(items: List<Pair<Int, String>>, columns: Int) {
    Column(
        Modifier.padding(start = HEAD_PADDING_H, top = Spacing.xxs, end = HEAD_PADDING_H, bottom = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items.chunked(columns).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                line.forEach { (label, value) -> FormInfoItem(label, value, Modifier.weight(1f)) }
                // 마지막 줄이 덜 차도 칸 폭을 위 줄과 맞춘다
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FormInfoItem(@StringRes label: Int, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(stringResource(label), color = ChagokColors.textSub, fontSize = TextSizes.caption)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
