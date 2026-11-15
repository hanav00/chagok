package kr.chagok.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.Trip
import kr.chagok.ui.components.FacePicker
import kr.chagok.ui.components.MiniButton
import kr.chagok.ui.components.MutedText
import kr.chagok.ui.components.Section
import kr.chagok.ui.components.SectionTitle
import kr.chagok.ui.components.shortLabelRes
import kr.chagok.ui.components.textColor
import kr.chagok.ui.components.tripTitle
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TouchSizes

/** 목록 한 줄의 위아래 여백 */
private val ROW_VERTICAL_PADDING = Spacing.s

/** 분류 대기: 기록 안 된 주행을 작은 분류 버튼(업무·개인)으로 1탭에 고른다 */
@Composable
internal fun PendingSection(trips: List<Trip>, onClassify: (tripId: Long, Category) -> Unit) {
    Section {
        SectionTitle(stringResource(R.string.home_gap_title), count = trips.size)
        Column {
            trips.forEach { trip ->
                HorizontalDivider(color = ChagokColors.divider)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ROW_VERTICAL_PADDING),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TripInfo(trip, detail = tripDetail(trip, withTime = false), modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(TouchSizes.gap)) {
                        Category.choicesFor(trip).forEach { category ->
                            MiniButton(
                                text = stringResource(category.shortLabelRes),
                                color = category.textColor,
                                onClick = { onClassify(trip.id, category) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 누가 탔는지 골라 주세요 (공용차 미지정 운행). 얼굴 줄이 길어 운행 정보 아래로 내린다.
 * 운전자가 못 고르는 지난 운행은 줄 대신 개수만 알린다.
 */
@Composable
internal fun UnassignedSection(
    state: UnassignedState,
    people: List<Person>,
    onPick: (tripId: Long, personId: Long) -> Unit,
) {
    Section {
        // 숫자는 지금 고를 수 있는 것만. 잠긴 지난 건은 아래 한 줄로 알린다
        SectionTitle(stringResource(R.string.unassigned_title), count = state.openTrips.size.takeIf { it > 0 })
        if (state.openTrips.isNotEmpty()) {
            Column {
                state.openTrips.forEach { trip ->
                    HorizontalDivider(color = ChagokColors.divider)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = ROW_VERTICAL_PADDING),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        TripInfo(trip, detail = tripDetail(trip, withTime = true))
                        FacePicker(people, selectedId = trip.personId, onPick = { onPick(trip.id, it) })
                    }
                }
            }
        }
        if (state.lockedCount > 0) {
            MutedText(stringResource(R.string.unassigned_past_locked, state.lockedCount), icon = Icons.Rounded.Lock)
        }
    }
}

@Composable
private fun TripInfo(trip: Trip, detail: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(tripTitle(trip), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, color = ChagokColors.textSub)
    }
}

/** "10.26 (월) · 14:05 · 12.3km". 기록 안 된 주행은 시각을 모른다 */
@Composable
private fun tripDetail(trip: Trip, withTime: Boolean): String = listOfNotNull(
    Formatters.day(trip.date),
    trip.endedAt?.takeIf { withTime }?.let(Formatters::time),
    stringResource(R.string.km_value, Formatters.km(trip.km)),
).joinToString(Formatters.SEPARATOR)
