package kr.chagok.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.CategoryDay
import kr.chagok.domain.model.Trip
import kr.chagok.ui.components.DotRow
import kr.chagok.ui.components.RuledDivider
import kr.chagok.ui.components.RuledRow
import kr.chagok.ui.components.WideHead
import kr.chagok.ui.components.color
import kr.chagok.ui.components.labelRes
import kr.chagok.ui.components.ruledCell
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

/** 오늘 요약 칸 순서 */
private val TODAY_ORDER = listOf(Category.BUSINESS, Category.COMMUTE, Category.PERSONAL)

/** 간 곳이 없을 때도 칸 높이가 같게 둔다 (줄바꿈 없는 공백 한 칸) */
private const val EMPTY_LINE = "\u00A0"

/**
 * 오늘 요약 (오늘 분류한 운행이 있을 때): 개인이었던 운행이 업무로 남아 있지 않은지 한눈에 본다.
 * 운행이 있는 분류만 칸을 만든다 ("출퇴근 0건" 같은 빈 칸은 읽을 거리만 늘린다).
 */
@Composable
internal fun TodayFacts(summary: Map<Category, CategoryDay>) {
    val facts = TODAY_ORDER.mapNotNull { category ->
        summary[category]?.takeIf { it.count > 0 }?.let { category to it }
    }
    if (facts.isEmpty()) return
    RuledRow {
        facts.forEachIndexed { index, (category, day) ->
            if (index > 0) RuledDivider()
            TodayFact(category, day, Modifier.weight(1f).ruledCell(isFirst = index == 0))
        }
    }
}

@Composable
private fun TodayFact(category: Category, day: CategoryDay, modifier: Modifier) {
    // 개인 운행은 장소를 남기지 않으니 간 곳 줄이 비어 있다 (칸 높이는 맞춘다)
    val places = if (category.isBusinessUse) day.placeNames.joinToString(Formatters.SEPARATOR) else ""
    Column(modifier) {
        Text(stringResource(category.todayLabelRes), color = ChagokColors.textSub, fontSize = TextSizes.caption)
        Text(
            text = stringResource(R.string.count_value, day.count) + Formatters.SEPARATOR +
                stringResource(R.string.km_value_spaced, Formatters.km(day.km)),
            modifier = Modifier.padding(top = Spacing.xxs),
            fontWeight = FontWeight.Bold,
        )
        // 오늘 간 곳 이름: 읽어야 하는 정보라 가장 흐린 색을 쓰지 않는다
        Text(
            text = places.ifEmpty { EMPTY_LINE },
            modifier = Modifier.padding(top = Spacing.xxs),
            color = ChagokColors.textSub,
            fontSize = TextSizes.caption,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@get:StringRes
private val Category.todayLabelRes: Int
    get() = when (this) {
        Category.BUSINESS -> R.string.today_business
        Category.COMMUTE -> R.string.today_commute
        Category.PERSONAL -> R.string.today_personal
    }

/** 넓은 창 홈의 오늘: 운행 하나에 한 줄 (분류 점 · 간 곳 · 거리). 개인 운행은 장소를 남기지 않아 분류 이름만 */
@Composable
internal fun TodayTrips(trips: List<Trip>) {
    if (trips.isEmpty()) return
    Column {
        WideHead(
            stringResource(R.string.home_today_title) + Formatters.SEPARATOR +
                stringResource(R.string.count_value, trips.size) + Formatters.SEPARATOR +
                stringResource(R.string.km_value_spaced, Formatters.km(trips.sumOf { it.km })),
        )
        trips.forEachIndexed { index, trip ->
            DotRow(
                color = trip.category.color,
                text = todayTripLabel(trip),
                value = stringResource(R.string.km_value_spaced, Formatters.km(trip.km)),
                isFirst = index == 0,
            )
        }
    }
}

@Composable
private fun todayTripLabel(trip: Trip): String = when {
    trip.isGap && trip.category == null -> stringResource(R.string.home_gap_title)
    // 내비 없이 온 곳은 이름 없이 주차한 자리 주소만 있다
    trip.category == Category.BUSINESS ->
        trip.destination?.displayName?.ifBlank { null } ?: stringResource(Category.BUSINESS.labelRes)
    else -> stringResource(trip.category?.labelRes ?: R.string.category_pending)
}
