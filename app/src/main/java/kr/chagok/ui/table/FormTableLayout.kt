package kr.chagok.ui.table

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.chagok.domain.model.DistanceTotals

/** ⑩비고 칸 최소 폭. 이만큼도 안 남으면 표 전체를 옆으로 넘긴다 */
private val REMARK_MIN_WIDTH = 150.dp

/**
 * 표를 그리는 데 필요한 것: 보일 칸, 줄마다 칸 글자, 칸 폭.
 * 줄이 LazyColumn 의 항목으로 따로 그려지므로, 칸 폭은 화면이 한 번 재서 모든 줄에 같이 준다.
 */
internal class FormTableLayout(
    val columns: List<FormColumn>,
    val rowCells: List<Map<FormColumn, FormCell>>,
    /** 월 합계 줄. null 이면 감춘다 */
    val totalLine: TotalLine?,
    val widths: Map<FormColumn, Dp>,
) {
    private val fixedWidth: Dp = widths.values.fold(0.dp, Dp::plus)

    /** 표 전체 폭: 보이는 폭을 채우고, 비고 최소 폭도 안 남으면 그만큼 넓혀 옆으로 넘긴다 */
    fun tableWidth(viewport: Dp): Dp = maxOf(viewport, fixedWidth + REMARK_MIN_WIDTH)
}

/** 월 합계 줄: 거리 칸 글자와 ⑩비고에 적을 분류 대기 거리 */
internal class TotalLine(val cells: Map<FormColumn, FormCell>, val pendingKm: Double)

/**
 * 칸 폭은 내용에 맞추고([rememberColumnWidths]), 비고가 남는 폭을 쓴다.
 * [totals] 가 null 이면 월 합계 줄을 감춘다 (잠겨서 오늘 운행만 보일 때).
 */
@Composable
internal fun rememberFormTableLayout(
    rows: List<TableRow>,
    totals: DistanceTotals?,
    isShowingAllColumns: Boolean,
): FormTableLayout {
    val columns = remember(isShowingAllColumns) { FormColumn.entries.filter { isShowingAllColumns || !it.isWideOnly } }
    val rowCells = rows.map { tripCells(it) }
    val totalLine = totals?.let { TotalLine(totalCells(it), it.pending) }
    val widths = rememberColumnWidths(columns, rowCells + listOfNotNull(totalLine?.cells))
    return FormTableLayout(columns, rowCells, totalLine, widths)
}
