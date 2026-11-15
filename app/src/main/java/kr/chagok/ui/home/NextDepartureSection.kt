package kr.chagok.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.CategoryDay
import kr.chagok.domain.model.Person
import kr.chagok.ui.components.Avatar
import kr.chagok.ui.components.DepartureToggle
import kr.chagok.ui.components.MutedText
import kr.chagok.ui.components.departureLabelRes
import kr.chagok.ui.components.personName
import kr.chagok.ui.components.pressable
import kr.chagok.ui.components.selectedFrame
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

/** 누가 타나요 버튼 칸: 이 너비보다 좁아지지 않게 한 줄 칸 수를 정한다 */
private val WHO_MIN_CELL_WIDTH = 150.dp
private val WHO_GAP = Spacing.s

/**
 * 다음 출발 [업무|개인] 토글 + (공용차) 누가 타나요 + 오늘 요약.
 * 시동마다 기본값으로 시작하고, 출발 전(P단)에 바꾸면 그 운행 하나에만 적용된다.
 */
@Composable
internal fun NextDepartureGroup(
    next: NextDepartureState,
    people: List<Person>,
    today: Map<Category, CategoryDay>?,
    onPickCategory: (Category) -> Unit,
    onPickPerson: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        NextCategory(next, onPickCategory)
        NextWho(next, people, onPickPerson)
        today?.let { TodayFacts(it) }
    }
}

/** 다음 출발 [업무|개인] 토글과 바꿨는지 알리는 한 줄 */
@Composable
internal fun NextCategory(next: NextDepartureState, onPick: (Category) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(R.string.next_label), fontSize = TextSizes.subtitle, fontWeight = FontWeight.SemiBold)
        DepartureToggle(next.category, onPick = onPick)
        if (next.isNoteShown) NoteLine(next)
    }
}

/** 공용차의 누가 타나요. 혼자 쓰는 차면 아무것도 그리지 않는다 */
@Composable
internal fun NextWho(next: NextDepartureState, people: List<Person>, onPick: (Long) -> Unit) {
    val who = next.who ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            stringResource(R.string.next_who),
            color = ChagokColors.textSub,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
        WhoButtons(people, who, onPick)
        if (who.isNoneNoteShown) {
            MutedText(
                stringResource(R.string.next_who_none),
                icon = Icons.Rounded.PersonOff,
                color = ChagokColors.pendingText,
            )
        }
    }
}

/** 바꿨으면 "다음 출발만 개인"(눈에 띄게), 아니면 "시동마다 기본값(업무)으로 시작해요"(사용법 안내 기간에만) */
@Composable
private fun NoteLine(next: NextDepartureState) {
    val text = if (next.isChanged) {
        stringResource(R.string.next_changed_note, stringResource(next.category.departureLabelRes))
    } else {
        stringResource(R.string.next_default_note, stringResource(next.defaultCategory.departureLabelRes))
    }
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = Spacing.xs),
        color = if (next.isChanged) ChagokColors.pending else ChagokColors.textDim,
        fontWeight = if (next.isChanged) FontWeight.SemiBold else FontWeight.Normal,
    )
}

/**
 * 이번에 누가 쓰나 (서식 ④사용자). 시동마다 비어 있고, 안 고르면 '사용자 미지정'으로 기록한다.
 * "지난번"은 실제로 마지막 운행을 한 사람에게만 붙인다 (고르라는 뜻이 아니라 참고).
 */
@Composable
private fun WhoButtons(people: List<Person>, who: WhoState, onPick: (Long) -> Unit) {
    AutoFitGrid(people, WHO_MIN_CELL_WIDTH, WHO_GAP) { person ->
        val isSelected = person.id == who.selectedId
        Column(
            modifier = Modifier
                .fillMaxSize()
                .selectedFrame(isSelected, Shapes.card, idleBackground = ChagokColors.card)
                .semantics { selected = isSelected }
                .pressable(role = Role.RadioButton) { onPick(person.id) }
                .padding(top = Spacing.l, bottom = Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Avatar(person.initial)
            Text(personName(person), fontSize = TextSizes.subtitle, fontWeight = FontWeight.Bold)
            Text(
                text = if (person.id == who.lastPersonId) stringResource(R.string.next_person_last) else person.dept,
                color = ChagokColors.textSub,
                fontSize = TextSizes.caption,
            )
        }
    }
}

/**
 * 최소 너비로 들어가는 만큼 한 줄 칸 수를 정하고, 칸보다 항목이 적으면 있는 것끼리 넓게 나눈다.
 * 한 줄의 칸은 높이를 맞춘다.
 */
@Composable
private fun <T> AutoFitGrid(
    items: List<T>,
    minCellWidth: Dp,
    gap: Dp,
    cell: @Composable (item: T) -> Unit,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fitColumns = ((maxWidth + gap) / (minCellWidth + gap)).toInt().coerceAtLeast(1)
        val columns = fitColumns.coerceAtMost(items.size)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    row.forEach { item ->
                        Box(Modifier.weight(1f).fillMaxHeight()) { cell(item) }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
