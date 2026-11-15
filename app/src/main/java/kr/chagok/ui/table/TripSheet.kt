package kr.chagok.ui.table

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.ui.components.FacePicker
import kr.chagok.ui.components.NoteButton
import kr.chagok.ui.components.SheetOverlay
import kr.chagok.ui.components.labelRes
import kr.chagok.ui.components.pressable
import kr.chagok.ui.components.shortLabelRes
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val CATEGORY_BUTTON_HEIGHT = 80.dp
private val PERSONAL_OUTLINE = 2.dp
private val NOTE_INSET = Spacing.xs
private const val NOTE_LINE_HEIGHT = 1.45f

/**
 * 운행 상세 시트: 기록부 줄을 누르면 앱 창 위로 열려 분류를 고치고 근거를 본다.
 * 개인으로 바꾸면 장소를 지우되, 시트를 닫기 전까지는 다시 업무로 바꾸면 살아난다 (TripRepository.classify).
 * 공용차의 미지정 운행은 여기서 사람을 고르고, 이미 정해진 사람을 바꾸는 것은 관리자만 한다.
 */
@Composable
fun TripSheet(
    tripId: Long,
    onDismiss: () -> Unit,
    viewModel: TripSheetViewModel = viewModel(factory = TripSheetViewModel.Factory),
) {
    LaunchedEffect(tripId) { viewModel.show(tripId) }
    // 장소 삭제는 사용자가 닫을 때만 확정한다. 화면이 다시 만들어지며 시트가 잠깐 치워질 때는 확정하지 않고,
    // 주행을 시작해 시트가 사라지면 기록기가 확정한다
    val dismiss = {
        viewModel.commitPlaceDeletion(tripId)
        onDismiss()
    }

    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val sheetState by viewModel.state.collectAsStateWithLifecycle()
    val state = sheetState?.takeIf { it.tripId == tripId } ?: return
    val trip = state.trip
    if (trip == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    SheetOverlay(
        onDismiss = dismiss,
        header = {
            Text(
                text = stringResource(R.string.sheet_title, Formatters.day(trip.date)),
                fontSize = TextSizes.title,
                fontWeight = FontWeight.Bold,
            )
        },
    ) {
        SheetLabel(R.string.sheet_category)
        CategoryPicker(choices = Category.choicesFor(trip), selected = trip.category, onPick = viewModel::classify)
        // 개인 운행은 장소 줄이 "닫으면 장소를 지워요"를 이미 말한다
        if (!trip.isPersonal) SheetNote(stringResource(R.string.sheet_work_note))
        if (state.isPersonPickable) {
            SheetLabel(R.string.sheet_user_pick)
            FacePicker(people = state.people, selectedId = trip.personId, onPick = viewModel::assignPerson)
        }
        // 방문 목적은 업무·출퇴근 운행만 (개인 운행은 장소처럼 남기지 않는다). 시트는 정차 중에만 열린다
        val isNoteEditable = !trip.isPersonal
        if (isNoteEditable) {
            SheetLabel(R.string.sheet_note)
            NoteButton(trip.note, isListening, viewModel::dictate, Modifier.fillMaxWidth())
        }
        TripFacts(
            trip = trip,
            person = state.person,
            isUnassigned = state.isUnassigned,
            isPlaceRestorable = state.isPlaceRestorable,
            isNoteShown = !isNoteEditable,
        )
    }
}

/** 분류: 업무 · 출퇴근 · 개인 (고른 쪽만 채운다. 개인은 테두리만) */
@Composable
private fun CategoryPicker(choices: List<Category>, selected: Category?, onPick: (Category) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ChagokColors.section, Shapes.card)
            .padding(Spacing.groupInset),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // 출퇴근과 나란히 놓이면 "업무"가 ⑨일반 업무만 뜻하므로 긴 이름을 쓴다
        val isThreeWay = Category.COMMUTE in choices
        choices.forEach { category ->
            val isOn = category == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(CATEGORY_BUTTON_HEIGHT)
                    .background(if (isOn) category.pickedBackground else Color.Transparent, Shapes.control)
                    .then(
                        if (isOn && category.isPersonal) {
                            Modifier.border(PERSONAL_OUTLINE, ChagokColors.textSub, Shapes.control)
                        } else {
                            Modifier
                        },
                    )
                    .semantics { this.selected = isOn }
                    .pressable(role = Role.RadioButton) { onPick(category) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(if (isThreeWay) category.labelRes else category.shortLabelRes),
                    color = if (isOn) category.pickedContent else ChagokColors.textSub,
                    fontSize = TextSizes.subtitle,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private val Category.isPersonal: Boolean get() = this == Category.PERSONAL

// 고른 분류 버튼 색. 업무·출퇴근은 분류 색으로 채운다
private val Category.pickedBackground: Color
    get() = when (this) {
        Category.BUSINESS -> ChagokColors.business
        Category.COMMUTE -> ChagokColors.commute
        Category.PERSONAL -> ChagokColors.cardStrong
    }

private val Category.pickedContent: Color
    get() = when (this) {
        Category.BUSINESS -> ChagokColors.onBusiness
        Category.COMMUTE -> ChagokColors.onCommute
        Category.PERSONAL -> ChagokColors.text
    }

@Composable
private fun SheetLabel(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        modifier = Modifier.padding(top = Spacing.xxs),
        color = ChagokColors.textSub,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SheetNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = NOTE_INSET),
        color = ChagokColors.textSub,
        style = LocalTextStyle.current.copy(lineHeight = TextSizes.body * NOTE_LINE_HEIGHT),
    )
}
