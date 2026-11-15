package kr.chagok.ui.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.Trip
import kr.chagok.ui.components.ActionButton
import kr.chagok.ui.components.ButtonStyle
import kr.chagok.ui.components.DotRow
import kr.chagok.ui.components.HeroNumber
import kr.chagok.ui.components.LockedLine
import kr.chagok.ui.components.MoreButton
import kr.chagok.ui.components.RoundIconButton
import kr.chagok.ui.components.WideGrid
import kr.chagok.ui.components.WideHead
import kr.chagok.ui.components.color
import kr.chagok.ui.components.labelRes
import kr.chagok.ui.components.tripTitle
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kr.chagok.ui.theme.TouchSizes

private const val RECENT_COUNT = 5

/** 분류별 줄 순서. null 은 분류 대기 (있을 때만) */
private val CATEGORY_ORDER: List<Category?> = listOf(Category.COMMUTE, Category.BUSINESS, Category.PERSONAL, null)

/**
 * 넓은 창 기록부 첫 화면: 이 달 거리 하나와 최근 운행 몇 줄. 국세청 서식 표는 "기록부 전체 보기"로 연다.
 * 왼쪽(운전석 쪽)에는 달 넘기기와 내보내기만 둔다.
 */
@Composable
internal fun TableOverview(
    state: TableUiState,
    onShiftMonth: (Long) -> Unit,
    onExport: () -> Unit,
    onShowForm: () -> Unit,
    onUnlock: () -> Unit,
    onOpenTrip: (Long) -> Unit,
) {
    val trips = state.rows.map { it.trip }
    WideGrid(
        topSide = { MonthAndExport(state, onShiftMonth, onExport) },
        topMain = {
            HeroNumber(
                label = if (state.isLocked) {
                    stringResource(R.string.table_overview_today)
                } else {
                    stringResource(R.string.table_overview_month, state.month.monthValue)
                },
                number = Formatters.km(state.monthTotals.total),
                unit = stringResource(R.string.km_unit),
            ) {
                Text(
                    stringResource(
                        R.string.table_overview_line,
                        trips.size,
                        Formatters.percent(state.yearTotals.businessRatio),
                    ),
                    color = ChagokColors.textSub,
                )
            }
        },
        bottomSide = {
            if (state.isLocked) LockedLine(stringResource(R.string.admin_locked_table), onUnlock = onUnlock)
            CategoryRows(trips)
        },
        bottomMain = { RecentTrips(trips, onShowForm, onOpenTrip) },
    )
}

@Composable
private fun MonthAndExport(state: TableUiState, onShiftMonth: (Long) -> Unit, onExport: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 잠겨 있으면 오늘 운행만 보이므로 달을 넘기지 않는다
        if (!state.isLocked) MonthButton(isNext = false, onShiftMonth)
        Text(
            Formatters.month(state.month),
            Modifier.weight(1f),
            fontSize = TextSizes.title,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (!state.isLocked) MonthButton(isNext = true, onShiftMonth)
    }
    ActionButton(
        text = stringResource(R.string.export_open),
        onClick = onExport,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
        icon = if (state.isLocked) Icons.Rounded.Lock else Icons.Rounded.QrCode2,
        style = ButtonStyle.Primary,
    )
    Text(
        stringResource(R.string.table_export_caption),
        Modifier.fillMaxWidth().padding(top = Spacing.m),
        color = ChagokColors.textSub,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MonthButton(isNext: Boolean, onShiftMonth: (Long) -> Unit) {
    RoundIconButton(
        icon = if (isNext) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
        contentDescription = stringResource(if (isNext) R.string.table_next_month else R.string.table_prev_month),
        onClick = { onShiftMonth(if (isNext) 1 else -1) },
        modifier = Modifier.size(TouchSizes.headButton),
        background = ChagokColors.card,
        tint = ChagokColors.text,
    )
}

@Composable
private fun CategoryRows(trips: List<Trip>) {
    val groups = CATEGORY_ORDER.map { category -> category to trips.filter { it.category == category } }
        .filter { (category, list) -> category != null || list.isNotEmpty() }
    Column {
        WideHead(stringResource(R.string.table_by_category))
        groups.forEachIndexed { index, (category, list) ->
            DotRow(
                color = category.color,
                text = stringResource(category?.labelRes ?: R.string.category_pending),
                note = stringResource(R.string.count_value, list.size),
                value = stringResource(R.string.km_value_spaced, Formatters.km(list.sumOf { it.km })),
                isFirst = index == 0,
            )
        }
    }
}

@Composable
private fun RecentTrips(trips: List<Trip>, onShowForm: () -> Unit, onOpenTrip: (Long) -> Unit) {
    WideHead(stringResource(R.string.table_recent)) {
        MoreButton(stringResource(R.string.table_show_form), onClick = onShowForm)
    }
    if (trips.isEmpty()) {
        Text(stringResource(R.string.table_empty_month), Modifier.padding(top = Spacing.m), color = ChagokColors.textSub)
        return
    }
    Column(verticalArrangement = Arrangement.Top) {
        trips.takeLast(RECENT_COUNT).asReversed().forEachIndexed { index, trip ->
            DotRow(
                color = trip.category.color,
                leading = Formatters.day(trip.date),
                text = routeLabel(trip),
                value = stringResource(R.string.km_value_spaced, Formatters.km(trip.km)),
                isFirst = index == 0,
                onClick = { onOpenTrip(trip.id) },
            )
        }
    }
}

// 개인 운행·기록 안 된 주행은 장소가 없어 운행 이름만
@Composable
private fun routeLabel(trip: Trip): String {
    val from = trip.origin?.displayName.orEmpty()
    if (trip.isPersonal || trip.isGap || from.isBlank()) return tripTitle(trip)
    return stringResource(R.string.sheet_place_value, from, tripTitle(trip))
}
