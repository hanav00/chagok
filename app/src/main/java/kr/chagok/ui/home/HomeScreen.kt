package kr.chagok.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.chagok.ui.components.ScreenColumn
import kr.chagok.ui.components.WideScreenPadding
import kr.chagok.ui.components.WideGrid
import kr.chagok.ui.components.WideSideWidth
import kr.chagok.ui.guide.GuideItem
import kr.chagok.ui.guide.StartGuideCard
import kr.chagok.ui.theme.Spacing

/**
 * 홈. 좁은 창은 할 일(방금 운행·분류 대기·미지정·주유 금액)을 위에, 볼거리는 맨 아래 요약 하나로 둔다.
 * 넓은 창은 스크롤 없는 한 화면을 두 줄로 맞춘다: 위는 다음 출발 | 업무사용비율, 아래는 오늘 | 이번 달 탑.
 * 왼쪽(운전석 쪽)에 누르는 것을 둔다. 방금 운행·기록 안 된 주행 장면이 있으면 그동안은 오른쪽에 그 장면이 선다.
 */
@Composable
fun HomeScreen(
    isWide: Boolean,
    onOpenDetail: () -> Unit,
    onGuideAction: (GuideItem) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val home = state ?: return
    val hasSummaryData = home.summary.yearTotals.total > 0
    val isSummaryIntro = remember(hasSummaryData) { hasSummaryData && viewModel.takeSummaryIntro() }

    if (!isWide) {
        ScreenColumn {
            Scenes(home, viewModel)
            if (home.isRecentClosed) RecentClosedLine(onReopen = viewModel::reopenRecent)
            TodoCards(home, viewModel, onGuideAction)
            NextDeparture(home, viewModel)
            SummaryCard(home.summary, isSummaryIntro)
        }
        return
    }
    val hasScene = home.recent != null || home.gap != null
    if (!hasScene) {
        WideGrid(
            topSide = { NextCategory(home.next, onPick = viewModel::pickNextCategory) },
            topMain = { StageRatio(home.summary, isSummaryIntro) },
            bottomSide = { WideSideBelow(home, viewModel, onGuideAction) },
            bottomMain = { StageMonth(home.summary, isSummaryIntro, onOpenDetail, Modifier.weight(1f)) },
        )
        return
    }
    // 방금 운행·기록 안 된 주행 장면은 블록이 크게 움직여서 그동안 오른쪽을 다 쓴다
    Row(
        Modifier.fillMaxSize().padding(WideScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.wideColumnGap),
    ) {
        Column(
            Modifier.width(WideSideWidth).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.wideBlockGap),
        ) {
            NextCategory(home.next, onPick = viewModel::pickNextCategory)
            WideSideBelow(home, viewModel, onGuideAction)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
        ) {
            Scenes(home, viewModel)
        }
    }
}

/** 넓은 창 왼쪽 아래: 누가 타나요 · 할 일 · 오늘 운행 · 계기판 이어짐 */
@Composable
private fun WideSideBelow(home: HomeUiState, viewModel: HomeViewModel, onGuideAction: (GuideItem) -> Unit) {
    NextWho(home.next, home.people, onPick = viewModel::pickNextPerson)
    if (home.isRecentClosed) RecentClosedLine(onReopen = viewModel::reopenRecent)
    TodoCards(home, viewModel, onGuideAction)
    TodayTrips(home.todayTrips)
    IntegrityLine(home.summary.odometerKm, home.summary.unrecordedKm)
}

/** 방금 운행 카드와 기록 안 된 주행 장면. 블록이 크게 움직이는 장면이라 넓은 창에선 무대 자리에 선다 */
@Composable
private fun Scenes(home: HomeUiState, viewModel: HomeViewModel) {
    home.recent?.let { card ->
        RecentCard(
            card = card,
            people = home.people,
            onClose = viewModel::dismissRecent,
            onLanded = viewModel::markRecentLanded,
            onListen = viewModel::dictate,
            onToPersonal = viewModel::recentToPersonal,
            onToWork = viewModel::recentToWork,
            onPickPerson = viewModel::pickRecentPerson,
        )
    }
    home.gap?.let { GapScene(it, onClassify = viewModel::classify) }
}

/** 시작 가이드·두 번째부터의 분류 대기·미지정·주유 금액. 다 하거나 닫으면 사라진다 */
@Composable
private fun TodoCards(home: HomeUiState, viewModel: HomeViewModel, onGuideAction: (GuideItem) -> Unit) {
    home.guide?.let { StartGuideCard(it, onAction = onGuideAction, onDismiss = viewModel::dismissGuide) }
    // 첫 분류 대기는 장면으로, 두 번째부터는 목록으로
    home.pendingTrips.drop(1).takeIf { it.isNotEmpty() }?.let { PendingSection(it, onClassify = viewModel::classify) }
    home.unassigned?.let { UnassignedSection(it, home.people, onPick = viewModel::assignPerson) }
    home.refuel?.let {
        RefuelSection(it, onSpeak = viewModel::dictateRefuelAmount, onSkip = viewModel::skipRefuelAmount)
    }
}

@Composable
private fun NextDeparture(home: HomeUiState, viewModel: HomeViewModel) {
    NextDepartureGroup(
        next = home.next,
        people = home.people,
        today = home.today,
        onPickCategory = viewModel::pickNextCategory,
        onPickPerson = viewModel::pickNextPerson,
    )
}
