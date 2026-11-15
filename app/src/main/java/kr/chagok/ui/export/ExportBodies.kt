package kr.chagok.ui.export

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.model.DistanceTotals
import kr.chagok.ui.components.ActionButton
import kr.chagok.ui.components.ButtonStyle
import kr.chagok.ui.components.FactCell
import kr.chagok.ui.components.MutedText
import kr.chagok.ui.components.WarningLine
import kr.chagok.ui.components.personName
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kotlin.math.roundToLong

private val BODY_GAP = Spacing.xl

/** 상자 없이 위아래만 띄운다 */
private val BODY_PADDING_V = Spacing.xs
private val INFO_MIN_WIDTH = 260.dp
private val INFO_GAP = Spacing.s
private val FACT_GAP = Spacing.l
private const val TITLE_LINE_HEIGHT = 1.4f

/**
 * 이 달 기록부: 운행 수와 거리, 내보내기 전에 고칠 것.
 * @param onEditPerson 이름 없는 사람의 사람 시트를 연다 (④성명 넣기)
 */
@Composable
internal fun MonthExportBody(export: MonthExport, onOpenTrip: (Long) -> Unit, onEditPerson: (Long) -> Unit) {
    ExportBody(
        qrSeed = "${export.month}-${export.tripCount}",
        towers = export.towers,
        bookTitle = stringResource(R.string.export_book_month, export.month.monthValue),
        tripCount = export.tripCount,
    ) {
        InfoTitle(stringResource(R.string.export_month_title, Formatters.month(export.month)))
        Facts(
            Fact(R.string.export_month_trips, stringResource(R.string.count_value, export.tripCount)),
            Fact(R.string.export_month_total, kmText(export.totals.total)),
            Fact(R.string.export_month_business, kmText(export.totals.businessUseKm)),
        )
        export.firstPendingId?.let { id ->
            FixWarning(
                text = stringResource(R.string.export_month_pending, export.pendingCount),
                button = stringResource(R.string.export_fix_pending),
                onClick = { onOpenTrip(id) },
            )
        }
        export.firstUnassignedId?.let { id ->
            FixWarning(
                text = stringResource(R.string.export_month_unassigned, export.unassignedCount),
                button = stringResource(R.string.export_fix_unassigned),
                onClick = { onOpenTrip(id) },
            )
        }
        export.unnamedPeople.firstOrNull()?.let { first ->
            val names = export.unnamedPeople.map { personName(it) }.joinToString(Formatters.SEPARATOR)
            FixWarning(
                text = stringResource(R.string.export_month_unnamed, names),
                button = stringResource(R.string.export_fix_unnamed),
                onClick = { onEditPerson(first.id) },
            )
        }
    }
}

/**
 * 올해 합계: 관련비용 명세서(별지 제29호서식) 1번 표 중 앱이 채울 수 있는 칸.
 * 번호는 명세서 기준이고, 괄호는 운행기록부 번호다 (명세서 ⑦⑧⑨ = 기록부 ⑪⑫⑬).
 */
@Composable
internal fun YearExportBody(export: YearExport, onOpenTrip: (Long) -> Unit) {
    val missing = stringResource(R.string.form_missing)
    val totals = export.totals
    ExportBody(
        qrSeed = "year-${export.fiscalYear.year}-${totals.total.roundToLong()}",
        towers = export.towers,
        bookTitle = export.fiscalYear.year.toString(),
        tripCount = export.tripCount,
    ) {
        InfoTitle(stringResource(R.string.export_year_title, export.fiscalYear.year))
        Facts(
            Fact(R.string.export_year_plate, export.vehicle.plate.ifBlank { missing }),
            Fact(R.string.export_year_model, export.vehicle.model.ifBlank { missing }),
            Fact(R.string.export_year_logged, stringResource(R.string.export_year_logged_value)),
        )
        Facts(
            Fact(R.string.export_year_total, kmText(totals.total)),
            Fact(R.string.export_year_business, kmText(totals.businessUseKm)),
            Fact(R.string.export_year_ratio, Formatters.percent(totals.businessRatio), isKey = true),
        )
        val breakdown = stringResource(
            R.string.export_year_breakdown,
            Formatters.km(totals.commute),
            Formatters.km(totals.business),
        )
        MutedText(breakdown)
        export.firstPendingId?.let { id ->
            FixWarning(
                text = stringResource(R.string.export_year_pending, Formatters.km(totals.pending)),
                button = stringResource(R.string.export_fix_pending),
                onClick = { onOpenTrip(id) },
            )
        }
        MutedText(stringResource(R.string.export_year_handoff), icon = Icons.AutoMirrored.Rounded.ReceiptLong)
    }
}

// QR(처음엔 탑 → 기록부 → QR 그림)과 옆 설명. 폭이 모자라면 설명이 QR 아래로 내려간다
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportBody(
    qrSeed: String,
    towers: List<DistanceTotals>,
    bookTitle: String,
    tripCount: Int,
    info: @Composable ColumnScope.() -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BODY_PADDING_V),
        horizontalArrangement = Arrangement.spacedBy(BODY_GAP, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(BODY_GAP),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ExportReveal(key = qrSeed, towers = towers, bookTitle = bookTitle, tripCount = tripCount) { ExportQr(qrSeed) }
        Column(
            modifier = Modifier.widthIn(min = INFO_MIN_WIDTH),
            verticalArrangement = Arrangement.spacedBy(INFO_GAP),
            content = info,
        )
    }
}

@Composable
private fun kmText(km: Double): String = stringResource(R.string.km_value, Formatters.km(km))

@Composable
private fun InfoTitle(text: String) {
    Text(
        text = text,
        fontSize = TextSizes.subtitle,
        fontWeight = FontWeight.Bold,
        style = LocalTextStyle.current.copy(lineHeight = TextSizes.subtitle * TITLE_LINE_HEIGHT),
    )
}

private class Fact(@StringRes val label: Int, val value: String, val isKey: Boolean = false)

// 이름표·값 칸을 옆으로 늘어놓고, 넘치면 다음 줄로
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(vararg facts: Fact) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(FACT_GAP),
        verticalArrangement = Arrangement.spacedBy(INFO_GAP),
    ) {
        facts.forEach { fact -> FactCell(fact.label, fact.value, isKey = fact.isKey) }
    }
}

/** 내보내기 전에 고칠 것 경고와 바로 고치러 가는 버튼 */
@Composable
internal fun FixWarning(text: String, button: String, onClick: () -> Unit, icon: ImageVector? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        WarningLine(text)
        ActionButton(
            text = button,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            icon = icon,
            style = ButtonStyle.Primary,
        )
    }
}
