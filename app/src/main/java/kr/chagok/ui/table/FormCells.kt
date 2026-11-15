package kr.chagok.ui.table

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.model.DistanceTotals
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.AppFontFamily
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

/** 칸 안쪽 여백 */
internal val CELL_PADDING_H = Spacing.s
internal val CELL_PADDING_V = Spacing.s

private val CHIP_PADDING_H = Spacing.s
private val CHIP_PADDING_V = Spacing.xxs
private val CHIP_BACKGROUND = ChagokColors.pending.copy(alpha = 0.16f)

/** 칸 사이 선 두께와 글자 폭 반올림 여유 */
private val WIDTH_SLACK = 2.dp

/** 숫자 칸은 숫자 폭을 같게 해 자리를 맞춘다 */
private const val TABULAR_NUMS = "tnum"
private const val HEADER_LINE_HEIGHT = 1.3f

internal val BODY_TEXT = TextStyle(
    fontFamily = AppFontFamily,
    fontSize = TextSizes.body,
    fontFeatureSettings = TABULAR_NUMS,
)
internal val HEADER_TEXT = TextStyle(
    fontFamily = AppFontFamily,
    fontSize = TextSizes.caption,
    fontWeight = FontWeight.SemiBold,
    color = ChagokColors.textSub,
    lineHeight = TextSizes.caption * HEADER_LINE_HEIGHT,
)
private val CHIP_TEXT = TextStyle(
    fontFamily = AppFontFamily,
    fontSize = TextSizes.caption,
    fontWeight = FontWeight.Bold,
)

/**
 * 국세청 별지 서식 칸 중 폭이 내용에 맞는 칸. ⑩비고는 남는 폭을 다 쓰는 칸이라 [formTable] 이 따로 그린다.
 * [isWideOnly] 칸은 분할 칸에서 "서식 전체 보기"를 눌렀을 때만 보인다.
 * ⑧⑨는 "업무용 사용거리" 묶음 머리 아래 두 칸이다. 비고 바로 앞 마지막 두 칸이어야 머리 줄이 맞는다.
 */
internal enum class FormColumn(
    @StringRes val headerRes: Int,
    val isWideOnly: Boolean = false,
    val isNumber: Boolean = false,
    val isInBusinessGroup: Boolean = false,
) {
    DATE(R.string.form_date),
    DEPT(R.string.form_user_dept, isWideOnly = true),
    NAME(R.string.form_user_name),
    START_KM(R.string.form_start_km, isWideOnly = true, isNumber = true),
    END_KM(R.string.form_end_km, isWideOnly = true, isNumber = true),
    KM(R.string.form_km, isNumber = true),
    COMMUTE(R.string.form_commute, isNumber = true, isInBusinessGroup = true),
    BUSINESS(R.string.form_business, isNumber = true, isInBusinessGroup = true),
}

/** 칸 하나의 글자. [isChip] 이면 주황 칩 ("미지정" · "이름 입력 필요") */
internal data class FormCell(val text: String, val isChip: Boolean = false)

/** 운행 한 줄의 ③~⑨ 칸 */
@Composable
internal fun tripCells(row: TableRow): Map<FormColumn, FormCell> {
    val distances = row.trip.formDistances()
    val dash = stringResource(R.string.sheet_empty_value)
    val name = when {
        row.person?.isNamed == true -> FormCell(row.person.name)
        row.person != null -> FormCell(stringResource(R.string.person_name_needed), isChip = true) // 이름 없이 등록한 사람
        row.isUnassigned -> FormCell(stringResource(R.string.table_unassigned), isChip = true)
        else -> FormCell(dash)
    }
    return mapOf(
        FormColumn.DATE to FormCell(Formatters.day(row.trip.date)),
        FormColumn.DEPT to FormCell(row.person?.dept?.ifBlank { null } ?: dash),
        FormColumn.NAME to name,
        FormColumn.START_KM to FormCell(Formatters.km(distances.startKm)),
        FormColumn.END_KM to FormCell(Formatters.km(distances.endKm)),
        FormColumn.KM to FormCell(Formatters.km(distances.km)),
        FormColumn.COMMUTE to FormCell(kmOrBlank(distances.commuteKm)),
        FormColumn.BUSINESS to FormCell(kmOrBlank(distances.businessKm)),
    )
}

/** 월 합계 줄. 거리 칸 말고는 비운다 */
@Composable
internal fun totalCells(totals: DistanceTotals): Map<FormColumn, FormCell> = mapOf(
    FormColumn.DATE to FormCell(stringResource(R.string.form_month_total)),
    FormColumn.KM to FormCell(Formatters.km(totals.total)),
    FormColumn.COMMUTE to FormCell(kmOrBlank(totals.commute)),
    FormColumn.BUSINESS to FormCell(kmOrBlank(totals.business)),
)

// ⑧⑨는 해당 분류가 아니면 비워 둔다 (서식에 0 을 적지 않는다)
private fun kmOrBlank(km: Double): String = if (km == 0.0) "" else Formatters.km(km)

/**
 * 칸 폭: 그 칸에서 가장 긴 글자(머리 포함) + 안쪽 여백. 내용에 맞추되 줄바꿈하지 않는다.
 * 굵은 글자 기준으로 재서 월 합계 줄(굵게)도 넘치지 않는다.
 * "업무용 사용거리" 묶음 머리가 ⑧+⑨보다 넓으면 모자란 만큼 ⑨에 더 준다.
 */
@Composable
internal fun rememberColumnWidths(
    columns: List<FormColumn>,
    lines: List<Map<FormColumn, FormCell>>,
): Map<FormColumn, Dp> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headers = columns.associateWith { stringResource(it.headerRes) }
    val groupHeader = stringResource(R.string.form_business_group)
    return remember(columns, lines, headers, groupHeader, density) {
        fun widthOf(text: String, style: TextStyle): Dp =
            with(density) { measurer.measure(text, style, softWrap = false).size.width.toDp() }

        fun widthOf(cell: FormCell): Dp = if (cell.isChip) {
            widthOf(cell.text, CHIP_TEXT) + CHIP_PADDING_H * 2
        } else {
            widthOf(cell.text, BODY_TEXT.copy(fontWeight = FontWeight.Bold))
        }

        val widths = columns.associateWith { column ->
            val content = lines.mapNotNull { it[column] }.maxOfOrNull(::widthOf) ?: 0.dp
            maxOf(widthOf(headers.getValue(column), HEADER_TEXT), content) + CELL_PADDING_H * 2 + WIDTH_SLACK
        }.toMutableMap()
        val groupNeed = widthOf(groupHeader, HEADER_TEXT) + CELL_PADDING_H * 2 + WIDTH_SLACK
        val shortfall = groupNeed - widths.getValue(FormColumn.COMMUTE) - widths.getValue(FormColumn.BUSINESS)
        if (shortfall > 0.dp) widths[FormColumn.BUSINESS] = widths.getValue(FormColumn.BUSINESS) + shortfall
        widths
    }
}

/** 분류 대기·미지정 칩 */
@Composable
internal fun PendingChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .background(CHIP_BACKGROUND, Shapes.pill)
            .padding(horizontal = CHIP_PADDING_H, vertical = CHIP_PADDING_V),
        color = ChagokColors.pending,
        style = CHIP_TEXT,
        maxLines = 1,
    )
}
