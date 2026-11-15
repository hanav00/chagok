package kr.chagok.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kr.chagok.R
import kr.chagok.ui.components.HeroNumberSize
import kr.chagok.ui.components.MoreButton
import kr.chagok.ui.components.WideHead
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.Spacing

/** 넓은 창 홈 오른쪽 위: 올해 업무사용비율 하나와 업무용 · 전체 거리 한 줄 */
@Composable
internal fun StageRatio(summary: SummaryState, isIntro: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        RatioHero(summary.fiscalYear, summary.yearTotals, isIntro, fontSize = HeroNumberSize, isFactsBelow = true)
    }
}

/**
 * 넓은 창 홈 오른쪽 아래: 이번 달 블록 탑이 남은 높이를 채운다.
 * 분류별 거리·계기판 같은 곁 정보는 "자세히"를 누르면 시트로 본다.
 */
@Composable
internal fun StageMonth(summary: SummaryState, isIntro: Boolean, onOpenDetail: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        WideHead(
            text = stringResource(R.string.home_month_title, Formatters.month(summary.month)) +
                Formatters.SEPARATOR + stringResource(R.string.km_value_spaced, Formatters.km(summary.monthTotals.total)),
            modifier = Modifier.padding(bottom = Spacing.l),
        ) {
            MoreButton(stringResource(R.string.home_detail), onClick = onOpenDetail)
        }
        MonthBlocks(summary.monthDays, summary.todayOfMonth, isIntro, Modifier.weight(1f), isStage = true)
    }
}
