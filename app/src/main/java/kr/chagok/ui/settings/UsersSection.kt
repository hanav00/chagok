package kr.chagok.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import kr.chagok.R
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.model.Person
import kr.chagok.ui.components.LockedLine
import kr.chagok.ui.components.RoundIconButton
import kr.chagok.ui.components.Section
import kr.chagok.ui.components.SectionTitle
import kr.chagok.ui.components.personName
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.people.PersonSheetTarget
import kr.chagok.ui.theme.TouchSizes

/**
 * 사용자 명단. 숨기지 않은 첫 사람이 기본 사용자이고, 숨긴 사람은 맨 아래에 둔다.
 * 차 안이라 끌어 옮기기 대신 큰 "위로" 버튼으로 순서를 바꾸고, 잠긴 차에서는 이 버튼을 감춘다.
 *
 * @param people 숨긴 사람까지 ([kr.chagok.data.PeopleRepository.allPeople])
 */
@Composable
internal fun UsersSection(
    people: List<Person>,
    isLocked: Boolean,
    isTitled: Boolean,
    onUnlock: () -> Unit,
    onEdit: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
) {
    val (visible, hidden) = people.partition { !it.isHidden }
    Section {
        if (isTitled) SectionTitle(stringResource(R.string.settings_users_title))
        if (isLocked) LockedLine(stringResource(R.string.admin_locked_setting), onUnlock = onUnlock)
        // 한 명뿐이면 바꿀 순서가 없다
        val isReorderable = !isLocked && visible.size > 1
        Column {
            visible.forEachIndexed { index, person ->
                PersonRow(
                    person = person,
                    isDefault = index == 0,
                    onClick = { onEdit(person.id) },
                    move = when {
                        !isReorderable -> MoveSlot.NONE
                        index == 0 -> MoveSlot.EMPTY
                        else -> MoveSlot.UP
                    },
                    onMoveUp = { onMoveUp(person.id) },
                )
            }
            hidden.forEach { person ->
                PersonRow(
                    person = person,
                    isDefault = false,
                    onClick = { onEdit(person.id) },
                    move = if (isReorderable) MoveSlot.EMPTY else MoveSlot.NONE,
                    onMoveUp = {},
                )
            }
            if (visible.size < ChagokConfig.PEOPLE_MAX) {
                LinkRow(stringResource(R.string.person_add)) { onEdit(PersonSheetTarget.NEW) }
            } else {
                SettingRow { RowLabel(stringResource(R.string.settings_people_full, ChagokConfig.PEOPLE_MAX)) }
            }
        }
    }
}

/** 줄 오른쪽 "위로" 자리. 버튼을 못 쓰는 줄(맨 위·숨긴 사람)은 빈칸을 둬서 다른 줄과 › 를 맞춘다 */
private enum class MoveSlot { UP, EMPTY, NONE }

/** 사람 한 줄. 이름 없는 사람·숨긴 사람은 이름 아래에 까닭을 적는다 */
@Composable
private fun PersonRow(person: Person, isDefault: Boolean, onClick: () -> Unit, move: MoveSlot, onMoveUp: () -> Unit) {
    val name = personName(person)
    val value = listOfNotNull(
        person.dept.ifBlank { null },
        stringResource(R.string.settings_default_user).takeIf { isDefault },
    ).joinToString(Formatters.SEPARATOR)
    val sub = when {
        person.isHidden -> stringResource(R.string.settings_people_hidden_sub)
        !person.isNamed -> stringResource(R.string.settings_people_unnamed_sub)
        else -> null
    }
    SettingRow(onClick = onClick) {
        RowLabel(name, sub)
        if (value.isNotBlank()) Text(value, fontWeight = FontWeight.Bold)
        when (move) {
            MoveSlot.UP -> RoundIconButton(
                icon = Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.settings_people_move_up, name),
                onClick = onMoveUp,
            )
            MoveSlot.EMPTY -> Spacer(Modifier.size(TouchSizes.round))
            MoveSlot.NONE -> Unit
        }
        EditChevron()
    }
}
