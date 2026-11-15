package kr.chagok.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.Trip
import kr.chagok.ui.components.ActionButton
import kr.chagok.ui.components.FacePicker
import kr.chagok.ui.components.FlightSpots
import kr.chagok.ui.components.IntroClock
import kr.chagok.ui.components.Hint
import kr.chagok.ui.components.MiniButton
import kr.chagok.ui.components.NoteButton
import kr.chagok.ui.components.RoundIconButton
import kr.chagok.ui.components.TripBlock
import kr.chagok.ui.components.dropping
import kr.chagok.ui.components.flightRoot
import kr.chagok.ui.components.flippingX
import kr.chagok.ui.components.reasonLabel
import kr.chagok.ui.components.rememberBlockDrop
import kr.chagok.ui.components.rememberFlip
import kr.chagok.ui.components.thudding
import kr.chagok.ui.components.tripTitle
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val NOW_BLOCK_HEIGHT = 96.dp
private val EARLIER_BLOCK_HEIGHT = 48.dp
private val STACK_MAX_WIDTH = 640.dp
private val STACK_GAP = Spacing.xs
private val DROP_DISTANCE = 420.dp
private val COUNT_NUMBER_SIZE = 64.sp

/** 탑에 보이는 앞선 운행 수 */
private const val STACK_EARLIER_MAX = 2

/**
 * 방금 운행 (도착·주차). 분류는 출발 때 이미 정해졌다.
 * 주차하면 방금 운행 블록이 오늘 탑 위로 떨어져 얹히고, 블록 안에 도착지·거리가 보인다.
 * 그 아래 계기판 줄과, 기록부에 적힐 서식 한 줄을 같이 보인다.
 * 업무면 방문 목적을 받아쓰기로 남기고, 잘못 잡혔으면 1탭으로 고친다(블록이 뒤집히며 색이 바뀐다).
 * 공용차에서 사람을 안 골랐으면 여기서 고른다.
 */
@Composable
internal fun RecentCard(
    card: RecentCardState,
    people: List<Person>,
    onClose: () -> Unit,
    onLanded: (tripId: Long) -> Unit,
    onListen: () -> Unit,
    onToPersonal: () -> Unit,
    onToWork: () -> Unit,
    onPickPerson: (Long) -> Unit,
) {
    val intro = rememberRecentIntro(card.trip.id, card.isLanded)
    val spots = remember { FlightSpots() }
    Box(
        Modifier
            .fillMaxWidth()
            .background(ChagokColors.card, Shapes.card)
            .flightRoot(spots),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            RecentTop(card, onClose)
            TodayStack(card, intro, onLanded)
            OdometerLine(card.trip, intro, spots)
            recentDetail(card)?.let { Text(it, color = ChagokColors.textSub) }
            if (card.hasFacePicker) {
                Text(stringResource(R.string.recent_who), color = ChagokColors.pendingText)
                FacePicker(people, selectedId = card.trip.personId, onPick = onPickPerson)
            }
            RecentFormLine(
                trip = card.trip,
                person = people.find { it.id == card.trip.personId },
                isUnassigned = card.hasFacePicker && card.trip.isUnassigned,
                intro = intro,
                spots = spots,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (card.isWork) {
                    NoteButton(card.trip.note, card.isListening, onListen, Modifier.weight(1f))
                    ActionButton(
                        text = stringResource(R.string.recent_to_personal),
                        onClick = onToPersonal,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.DirectionsCar,
                    )
                } else {
                    ActionButton(
                        text = stringResource(R.string.recent_to_business),
                        onClick = onToWork,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Work,
                    )
                }
            }
            if (card.hasHint) {
                val hint = when {
                    card.trip.category == Category.COMMUTE -> R.string.recent_hint_commute
                    card.isWork -> R.string.recent_hint_work
                    else -> R.string.recent_hint_off
                }
                Hint(stringResource(hint), isBoxed = false)
            }
        }
        RecentFlyers(card.trip, intro, spots)
    }
}

/** 방금 닫은 카드 → 잠깐 남는 한 줄. 실수로 닫았을 때 다시 연다 */
@Composable
internal fun RecentClosedLine(onReopen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChagokColors.card, Shapes.card)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = Spacing.l, top = Spacing.s, end = Spacing.s, bottom = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = ChagokColors.green)
        Text(stringResource(R.string.recent_closed), color = ChagokColors.textSub, modifier = Modifier.weight(1f))
        MiniButton(stringResource(R.string.recent_reopen), color = ChagokColors.text, onClick = onReopen)
    }
}

// "오늘 3번째" 와 닫기
@Composable
private fun RecentTop(card: RecentCardState, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(todayCountText(card.earlierToday.size + 1), color = ChagokColors.textSub, fontSize = TextSizes.title,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        RoundIconButton(Icons.Rounded.Close, contentDescription = stringResource(R.string.close), onClick = onClose)
    }
}

// 숫자만 크고 흰색: "오늘 [3]번째"
@Composable
private fun todayCountText(count: Int): AnnotatedString {
    val text = stringResource(R.string.recent_today_count, count)
    val number = count.toString()
    return buildAnnotatedString {
        append(text)
        val at = text.indexOf(number)
        if (at >= 0) {
            addStyle(SpanStyle(color = ChagokColors.text, fontSize = COUNT_NUMBER_SIZE), at, at + number.length)
        }
    }
}

/**
 * 오늘 운행 탑. 아래는 오늘 앞서 쌓인 운행(낮은 블록), 맨 위가 방금 운행(큰 블록)이다.
 * 카드가 처음 뜰 때 방금 운행이 위에서 떨어져 얹히고 아래 블록이 눌린 뒤, 계기판 숫자가 서식 칸으로 옮겨 간다.
 * 한 번 얹힌 운행은 다시 떨어지지 않는다.
 */
@Composable
private fun TodayStack(card: RecentCardState, intro: IntroClock, onLanded: (Long) -> Unit) {
    val drop = rememberBlockDrop(card.trip.id, isLanded = card.isLanded)
    LaunchedEffect(card.trip.id) {
        if (!drop.isLanded) {
            drop.play()
            onLanded(card.trip.id)
        }
        intro.play()
    }
    val flip = rememberFlip(card.trip.category)
    Column(
        modifier = Modifier.widthIn(max = STACK_MAX_WIDTH).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(STACK_GAP),
    ) {
        TripBlock(
            category = flip.shown,
            isGap = card.trip.isGap,
            shape = Shapes.blockLarge,
            modifier = Modifier
                .fillMaxWidth()
                .height(NOW_BLOCK_HEIGHT)
                .dropping(drop, DROP_DISTANCE)
                .flippingX(flip),
        ) {
            val isPersonal = flip.shown == Category.PERSONAL
            val icon = if (isPersonal) Icons.Rounded.DirectionsCar else Icons.Rounded.Flag
            Icon(icon, contentDescription = null, tint = ChagokColors.onBright)
            Text(
                text = blockLabel(card.trip, isPersonal),
                color = ChagokColors.onBright,
                fontSize = TextSizes.headline,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        card.earlierToday.takeLast(STACK_EARLIER_MAX).asReversed().forEach { block ->
            TripBlock(
                category = block.category,
                isGap = block.isGap,
                shape = Shapes.blockLarge,
                modifier = Modifier.fillMaxWidth().height(EARLIER_BLOCK_HEIGHT).thudding(drop),
            )
        }
    }
}

/** 블록 안 글자: "○○상사 · 12.4 km", 개인이면 "개인 · 12.4 km" */
@Composable
private fun blockLabel(trip: Trip, isPersonal: Boolean): String {
    val place = if (isPersonal) stringResource(R.string.trip_personal) else tripTitle(trip)
    val km = stringResource(R.string.km_value_spaced, Formatters.km(trip.km))
    return listOf(place, km).joinToString(Formatters.SEPARATOR)
}

/**
 * 블록 아래 흐린 한 줄: "10:38 · 처음 간 곳 · 사용자 수정". 거리·분류는 블록이 보여 준다.
 * 방금 개인으로 바꿨으면 장소를 아직 되돌릴 수 있어 "카드를 닫으면 장소를 지워요"를 보인다.
 */
@Composable
private fun recentDetail(card: RecentCardState): String? {
    val trip = card.trip
    val undo = if (!card.isWork && card.canRestorePlaces) stringResource(R.string.recent_personal_sub_undo) else null
    val firstVisit = if (card.isFirstVisit) stringResource(R.string.recent_first_visit) else null
    val reason = if (trip.reason?.isChangedByUser == true) reasonLabel(trip) else null
    return listOfNotNull(trip.endedAt?.let(Formatters::time), firstVisit, reason, undo)
        .joinToString(Formatters.SEPARATOR)
        .ifEmpty { null }
}
