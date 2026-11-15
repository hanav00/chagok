package kr.chagok.ui.table

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kr.chagok.R
import kr.chagok.ui.admin.PinMode
import kr.chagok.ui.components.Hint
import kr.chagok.ui.components.LockedLine
import kr.chagok.ui.components.RoundIconButton
import kr.chagok.ui.components.ScreenPadding
import kr.chagok.ui.components.WideScreenPadding
import kr.chagok.ui.components.SourceText
import kr.chagok.ui.export.ExportScreen
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing

/**
 * 운행기록부 탭. 국세청 별지 서식과 같은 칸 구성이고, 좁은 분할 창에서는 핵심 칸만 보인다.
 * 넓은 창은 이 달 거리와 최근 운행만 먼저 보이고, 서식 표는 한 번 더 눌러 연다.
 * 개인 운행은 장소를 적지 않는다 (사생활 보호, 서식에도 필요 없음).
 * 관리자 PIN 을 정한 차가 잠겨 있으면 오늘 운행만 보이고, 내보내기는 PIN 을 받은 뒤에 연다.
 */
@Composable
fun TableScreen(
    isWide: Boolean,
    onOpenTrip: (Long) -> Unit,
    onOpenPin: (PinMode, () -> Unit) -> Unit,
    onFillVehicle: () -> Unit,
    onEditPerson: (Long) -> Unit,
    viewModel: TableViewModel = viewModel(factory = TableViewModel.Factory),
) {
    val tableState by viewModel.state.collectAsStateWithLifecycle()
    val isFormHeadOpen by viewModel.isFormHeadOpen.collectAsStateWithLifecycle()
    var isExporting by rememberSaveable { mutableStateOf(false) }
    var isShowingAllColumns by rememberSaveable { mutableStateOf(false) }
    // 넓은 창은 요약을 먼저 보이고, 서식 표는 "기록부 전체 보기"로 연다
    var isShowingForm by rememberSaveable { mutableStateOf(false) }
    // 내보내기를 열었다 닫아도 보던 자리가 남게 아래 조기 반환보다 먼저 만든다
    val listState = rememberLazyListState()
    val columnScroll = rememberScrollState() // 표 가로 넘김: 머리 줄과 모든 줄이 같이 쓴다

    BackHandler(enabled = isExporting) { isExporting = false }
    BackHandler(enabled = isWide && isShowingForm && !isExporting) { isShowingForm = false }
    // 잠기면(관리자 잠그기) 내보내기 화면도 닫는다. 다시 풀어도 기록부부터
    LaunchedEffect(tableState?.isLocked) {
        if (tableState?.isLocked == true) isExporting = false
    }

    val state = tableState ?: return
    // 달이 바뀌거나 잠금을 풀면 서식 머리부터 보인다. 최근 줄은 오른쪽 아래 버튼으로 한 번에 내려간다
    LaunchedEffect(state.month, state.isLocked) {
        listState.scrollToItem(0)
    }

    if (isExporting) {
        ExportScreen(
            month = state.month,
            isWide = isWide,
            onBack = { isExporting = false },
            onOpenTrip = onOpenTrip,
            onFillVehicle = onFillVehicle,
            onEditPerson = onEditPerson,
        )
        return
    }

    val onExport = {
        if (state.isLocked) {
            onOpenPin(PinMode.UNLOCK) { isExporting = true }
        } else {
            isExporting = true
        }
    }
    val onUnlock = { onOpenPin(PinMode.UNLOCK) {} }
    if (isWide && !isShowingForm) {
        TableOverview(
            state = state,
            onShiftMonth = viewModel::shiftMonth,
            onExport = onExport,
            onShowForm = { isShowingForm = true },
            onUnlock = onUnlock,
            onOpenTrip = onOpenTrip,
        )
        return
    }

    TableBody(
        state = state,
        isWide = isWide,
        isShowingAllColumns = isShowingAllColumns,
        isFormHeadOpen = isFormHeadOpen ?: isWide,
        listState = listState,
        columnScroll = columnScroll,
        onShiftMonth = viewModel::shiftMonth,
        onExport = onExport,
        onToggleColumns = { isShowingAllColumns = !isShowingAllColumns },
        onToggleFormHead = viewModel::setFormHeadOpen,
        onUnlock = onUnlock,
        onOpenTrip = onOpenTrip,
        onBack = if (isWide) ({ isShowingForm = false }) else null,
    )
}

// 표 머리 줄이 화면 위에 붙어야 해서 위쪽 줄부터 사업연도 합계까지 LazyColumn 하나에 넣는다
@Composable
private fun TableBody(
    state: TableUiState,
    isWide: Boolean,
    isShowingAllColumns: Boolean,
    isFormHeadOpen: Boolean,
    listState: LazyListState,
    columnScroll: ScrollState,
    onShiftMonth: (Long) -> Unit,
    onExport: () -> Unit,
    onToggleColumns: () -> Unit,
    onToggleFormHead: (Boolean) -> Unit,
    onUnlock: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onBack: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val isShowingEndButton by remember { derivedStateOf { listState.canScrollForward } }
    // 잠겨서 오늘 운행만 보일 때는 월 합계 줄을 감춘다
    val totals = state.monthTotals.takeUnless { state.isLocked }
    val tableLayout = rememberFormTableLayout(state.rows, totals, isShowingAllColumns = isWide || isShowingAllColumns)
    val emptyText = stringResource(if (state.isLocked) R.string.table_empty_today else R.string.table_empty_month)
    val lockedText = stringResource(R.string.admin_locked_table)
    val hintText = stringResource(R.string.table_hint)
    val sourceText = stringResource(R.string.form_source)

    // 넓은 창은 헤더와 같은 폭으로 가장자리를 띄운다
    val horizontal = if (isWide) Spacing.wideHorizontal else Spacing.screenHorizontal
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tableWidth = tableLayout.tableWidth(maxWidth - horizontal * 2)
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = if (isWide) WideScreenPadding else ScreenPadding,
        ) {
            item {
                TableHead(
                    monthLabel = Formatters.month(state.month),
                    isLocked = state.isLocked,
                    isWide = isWide,
                    isShowingAllColumns = isShowingAllColumns,
                    onShiftMonth = onShiftMonth,
                    onExport = onExport,
                    onToggleColumns = onToggleColumns,
                    onBack = onBack,
                )
            }
            block {
                FormHead(
                    vehicle = state.vehicle,
                    fiscalYear = state.fiscalYear,
                    isOpen = isFormHeadOpen,
                    isWide = isWide,
                    onToggle = onToggleFormHead,
                )
            }
            if (state.isLocked) block { LockedLine(lockedText, onUnlock = onUnlock) }
            if (state.isHintPeriod) block { Hint(hintText, icon = Icons.Rounded.TouchApp) }
            item { Spacer(Modifier.height(Spacing.blockGap)) } // 머리 줄이 위에 붙을 때 틈이 비치지 않게 따로 띄운다
            formTable(
                rows = state.rows,
                layout = tableLayout,
                tableWidth = tableWidth,
                scroll = columnScroll,
                emptyText = emptyText,
                onOpenTrip = onOpenTrip,
            )
            block { YearTotals(state.yearTotals) }
            block { SourceText(sourceText) }
        }
        if (isShowingEndButton) {
            ScrollEndButton(
                onClick = { scope.launch { listState.animateScrollToEnd() } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal),
            )
        }
    }
}

// 덩어리 사이 간격. LazyColumn 전체에 간격을 주면 표 줄 사이도 벌어져서 덩어리에만 준다
private fun LazyListScope.block(content: @Composable () -> Unit) = item {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.blockGap)) { content() }
}

// 마지막 항목으로 가면 목록 끝이 화면 아래에 맞춰진다. 처음 그리기 전이면 항목 수가 잡힐 때까지 기다린다
private suspend fun LazyListState.animateScrollToEnd() = animateScrollToItem(awaitLastIndex())

private suspend fun LazyListState.awaitLastIndex(): Int =
    snapshotFlow { layoutInfo.totalItemsCount }.first { it > 0 } - 1

// 표를 올려 보다가 한 번에 최근 줄로
@Composable
private fun ScrollEndButton(onClick: () -> Unit, modifier: Modifier) {
    RoundIconButton(
        icon = Icons.Rounded.KeyboardDoubleArrowDown,
        contentDescription = stringResource(R.string.table_scroll_end),
        onClick = onClick,
        modifier = modifier,
        background = ChagokColors.cardStrong,
        tint = ChagokColors.text,
    )
}
