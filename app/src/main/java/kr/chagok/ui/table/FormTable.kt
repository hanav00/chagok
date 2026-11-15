package kr.chagok.ui.table

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing

private val EMPTY_PADDING = Spacing.xxl
private val GAP_MARK_WIDTH = 4.dp
private val PRESSED_ROW = ChagokColors.accent.copy(alpha = 0.12f)
private val PENDING_ROW = ChagokColors.pending.copy(alpha = 0.08f)

/** 표 위·아래 끝 줄만 모서리를 둥글린다 (표 전체가 Shapes.control 상자처럼 보이게) */
private val TOP_SHAPE = Shapes.control.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
private val BOTTOM_SHAPE = Shapes.control.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)

private const val HEADER_KEY = "form-header"
private const val EMPTY_KEY = "form-empty"
private const val TOTAL_KEY = "form-total"

/**
 * 운행기록부 표: 국세청 별지 서식 칸 순서 그대로. 줄을 누르면 [onOpenTrip].
 * 화면의 LazyColumn 에 머리 줄(stickyHeader)·운행 줄·월 합계 줄을 넣고, 넘치면 옆으로 넘긴다.
 * 줄마다 넘기지만 [scroll] 하나를 같이 써서 머리와 줄이 함께 움직인다.
 */
internal fun LazyListScope.formTable(
    rows: List<TableRow>,
    layout: FormTableLayout,
    tableWidth: Dp,
    scroll: ScrollState,
    emptyText: String,
    onOpenTrip: (Long) -> Unit,
) {
    val columns = layout.columns
    val widths = layout.widths
    val totalLine = layout.totalLine
    val hasTotal = totalLine != null
    stickyHeader(key = HEADER_KEY) {
        TableLine(scroll, tableWidth, isTop = true, isBottom = false) { HeaderRows(columns, widths) }
    }
    if (rows.isEmpty()) {
        item(key = EMPTY_KEY) {
            TableLine(scroll, tableWidth, isTop = false, isBottom = !hasTotal) { EmptyRow(emptyText) }
        }
    }
    itemsIndexed(rows, key = { _, row -> row.trip.id }) { index, row ->
        TableLine(scroll, tableWidth, isTop = false, isBottom = !hasTotal && index == rows.lastIndex) {
            TripRow(row, layout.rowCells[index], columns, widths, onClick = { onOpenTrip(row.trip.id) })
        }
    }
    if (totalLine != null) {
        item(key = TOTAL_KEY) {
            TableLine(scroll, tableWidth, isTop = false, isBottom = true) { TotalRow(totalLine, columns, widths) }
        }
    }
}

/**
 * 표 한 줄 틀: 옆으로 넘기고 가장자리를 흐린다. 바깥 왼쪽 선(머리 줄은 위쪽 선도)을 긋는다.
 * 머리 줄은 아래로 지나가는 줄 위에 붙어 있으므로 바탕을 채워 흐린 가장자리로 줄이 비치지 않게 한다.
 */
@Composable
private fun TableLine(
    scroll: ScrollState,
    width: Dp,
    isTop: Boolean,
    isBottom: Boolean,
    content: @Composable () -> Unit,
) {
    val shape = when {
        isTop -> TOP_SHAPE
        isBottom -> BOTTOM_SHAPE
        else -> RectangleShape
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (isTop) Modifier.background(ChagokColors.window) else Modifier)
            .horizontalEdgeFade(scroll)
            .horizontalScroll(scroll),
    ) {
        Box(Modifier.width(width).outerGridLines(isTop)) { content() }
    }
}

// 두 줄 머리: ⑧⑨ 위에 "업무용 사용거리(km)"를 걸치고, 나머지 칸은 두 줄 높이.
// 걸친 칸은 제 높이만 쓴다 (세로를 채우면 아래 ⑧⑨ 줄이 높이 0 이 된다)
@Composable
private fun HeaderRows(columns: List<FormColumn>, widths: Map<FormColumn, Dp>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(ChagokColors.section)) {
        columns.filterNot(FormColumn::isInBusinessGroup).forEach { column ->
            HeaderCell(stringResource(column.headerRes), Modifier.width(widths.getValue(column)).fillMaxHeight())
        }
        val commute = widths.getValue(FormColumn.COMMUTE)
        val business = widths.getValue(FormColumn.BUSINESS)
        Column(Modifier.width(commute + business).fillMaxHeight()) {
            HeaderCell(stringResource(R.string.form_business_group), Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().weight(1f)) {
                HeaderCell(stringResource(R.string.form_commute), Modifier.width(commute).fillMaxHeight())
                HeaderCell(stringResource(R.string.form_business), Modifier.width(business).fillMaxHeight())
            }
        }
        HeaderCell(stringResource(R.string.form_remark), Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Box(
        modifier.gridLines().padding(horizontal = CELL_PADDING_H, vertical = CELL_PADDING_V),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HEADER_TEXT, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyRow(text: String) {
    Box(Modifier.fillMaxWidth().gridLines().padding(EMPTY_PADDING), contentAlignment = Alignment.Center) {
        Text(text, color = ChagokColors.textSub)
    }
}

// 분류 대기 줄은 주황 바탕. 누르는 동안은 물결 대신 줄 바탕을 강조색으로 칠한다
@Composable
private fun TripRow(
    row: TableRow,
    cells: Map<FormColumn, FormCell>,
    columns: List<FormColumn>,
    widths: Map<FormColumn, Dp>,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val background = when {
        isPressed -> PRESSED_ROW
        row.trip.isPending -> PENDING_ROW
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(background)
            .semantics(mergeDescendants = true) {}
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = stringResource(R.string.table_open_trip),
                onClick = onClick,
            ),
    ) {
        columns.forEachIndexed { index, column ->
            val isGapMark = index == 0 && row.trip.isGap
            BodyCell(column, cells[column], widths.getValue(column), if (isGapMark) Modifier.gapMark() else Modifier)
        }
        RemarkCell { Remark(row) }
    }
}

@Composable
private fun TotalRow(line: TotalLine, columns: List<FormColumn>, widths: Map<FormColumn, Dp>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(ChagokColors.section)) {
        columns.forEach { column -> BodyCell(column, line.cells[column], widths.getValue(column), isBold = true) }
        RemarkCell {
            if (line.pendingKm > 0) {
                Text(
                    text = stringResource(R.string.table_pending_km, Formatters.km(line.pendingKm)),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun BodyCell(
    column: FormColumn,
    cell: FormCell?,
    width: Dp,
    modifier: Modifier = Modifier,
    isBold: Boolean = false,
) {
    Box(
        modifier
            .width(width)
            .fillMaxHeight()
            .gridLines()
            .padding(horizontal = CELL_PADDING_H, vertical = CELL_PADDING_V),
        contentAlignment = if (column.isNumber) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        when {
            cell == null -> Unit
            cell.isChip -> PendingChip(cell.text)
            else -> Text(
                text = cell.text,
                style = BODY_TEXT,
                fontWeight = if (isBold) FontWeight.Bold else null,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun RowScope.RemarkCell(content: @Composable () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .gridLines()
            .padding(horizontal = CELL_PADDING_H, vertical = CELL_PADDING_V),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

// 기록 안 된 주행 줄 왼쪽의 주황 띠
private fun Modifier.gapMark(): Modifier = drawWithContent {
    drawContent()
    drawRect(ChagokColors.pending, size = Size(GAP_MARK_WIDTH.toPx(), size.height))
}
