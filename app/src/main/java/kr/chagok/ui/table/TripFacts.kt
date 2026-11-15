package kr.chagok.ui.table

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kr.chagok.R
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.Trip
import kr.chagok.ui.components.personName
import kr.chagok.ui.components.reasonLabel
import kr.chagok.ui.components.tripTitle
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing

/**
 * 운행 상세의 사실 목록: 날짜·시각, 사용자, 계기판, 거리, 장소, 방문 목적, 분류 근거.
 * 분류 근거는 세무 소명 때 누가 정했는지 보여 주는 칸이라 기본값이어도 그대로 적는다.
 * 시트 위쪽 받아쓰기 버튼이 방문 목적을 보여 주면 [isNoteShown] 을 꺼서 두 번 적지 않는다.
 */
@Composable
internal fun TripFacts(
    trip: Trip,
    person: Person?,
    isUnassigned: Boolean,
    isPlaceRestorable: Boolean,
    isNoteShown: Boolean,
) {
    val none = stringResource(R.string.sheet_empty_value)
    val facts = listOfNotNull(
        R.string.sheet_when to whenLine(trip),
        R.string.sheet_user to (userLine(person, isUnassigned) ?: none),
        R.string.sheet_odometer to
            stringResource(R.string.sheet_odometer_value, Formatters.km(trip.startKm), Formatters.km(trip.endKm)),
        R.string.sheet_distance to stringResource(R.string.km_value_spaced, Formatters.km(trip.km)),
        R.string.sheet_place to placeLine(trip, isPlaceRestorable),
        (R.string.sheet_note to trip.note.ifBlank { none }).takeIf { isNoteShown },
        R.string.sheet_reason to (reasonLabel(trip) ?: none),
    )
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xxs)) {
        HorizontalDivider(color = ChagokColors.divider)
        facts.forEach { (label, value) ->
            FactRow(label, value)
            HorizontalDivider(color = ChagokColors.divider)
        }
    }
}

@Composable
private fun FactRow(@StringRes label: Int, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.xxs, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(stringResource(label), color = ChagokColors.textSub)
        Text(value, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

// "10.26 (월) · 09:12 ~ 09:48". 기록 안 된 주행은 시각을 몰라 날짜만
@Composable
private fun whenLine(trip: Trip): String {
    val day = Formatters.day(trip.date)
    val start = trip.startedAt?.let(Formatters::time)
    val end = trip.endedAt?.let(Formatters::time)
    val time = when {
        start != null && end != null -> stringResource(R.string.sheet_time_range, start, end)
        else -> start ?: end
    }
    return if (time == null) day else day + Formatters.SEPARATOR + time
}

// 사람이 없으면 공용차의 미지정만 "미지정", 그 밖(혼자 쓰는 차·기록 안 된 주행)은 null ("-")
@Composable
private fun userLine(person: Person?, isUnassigned: Boolean): String? = when {
    person == null -> if (isUnassigned) stringResource(R.string.sheet_user_none) else null
    person.dept.isBlank() -> personName(person)
    else -> personName(person) + Formatters.SEPARATOR + person.dept
}

// 개인 운행은 장소가 없다. 방금 개인으로 바꿔 아직 되살릴 수 있으면 닫을 때 지운다고 알린다.
// 도착지는 출발지처럼 이름(없으면 주소)만 적는다. 주소도 없을 때만 운행 이름("내비 미사용" 등)
@Composable
private fun placeLine(trip: Trip, isPlaceRestorable: Boolean): String {
    if (trip.isPersonal) {
        return stringResource(if (isPlaceRestorable) R.string.sheet_place_undo else R.string.sheet_place_none)
    }
    if (trip.isGap) return stringResource(R.string.sheet_place_gap)
    val from = trip.origin?.displayName.orEmpty()
    val to = trip.destination?.displayName?.ifBlank { null } ?: tripTitle(trip)
    return if (from.isBlank()) to else stringResource(R.string.sheet_place_value, from, to)
}
