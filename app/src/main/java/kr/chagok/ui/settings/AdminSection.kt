package kr.chagok.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import kr.chagok.R
import kr.chagok.domain.model.AdminEdit
import kr.chagok.domain.model.AdminState
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.Person
import kr.chagok.ui.admin.PinMode
import kr.chagok.ui.components.MutedText
import kr.chagok.ui.components.Section
import kr.chagok.ui.components.SectionTitle
import kr.chagok.ui.components.labelRes
import kr.chagok.ui.components.personName
import kr.chagok.ui.format.Formatters

/**
 * 관리자 칸. PIN 이 없으면 정하기, 잠겼으면 넣기, 풀렸으면 잠그기·바꾸기·넘기기와 수정 기록을 보인다.
 * 잠그기만 바로 하고 나머지는 PIN 시트에서 한다.
 */
@Composable
internal fun AdminSection(
    admin: AdminState,
    recentEdits: List<AdminEdit>,
    people: List<Person>,
    onOpenPin: (PinMode) -> Unit,
    onLock: () -> Unit,
) {
    Section {
        SectionTitle(stringResource(R.string.admin_title))
        when {
            !admin.hasPin -> Column { LinkRow(stringResource(R.string.admin_setup)) { onOpenPin(PinMode.SETUP) } }
            !admin.isUnlocked -> Column {
                LinkRow(stringResource(R.string.admin_unlock_button)) { onOpenPin(PinMode.UNLOCK) }
            }
            else -> UnlockedAdmin(recentEdits, people, onOpenPin, onLock)
        }
    }
}

@Composable
private fun UnlockedAdmin(
    recentEdits: List<AdminEdit>,
    people: List<Person>,
    onOpenPin: (PinMode) -> Unit,
    onLock: () -> Unit,
) {
    Column {
        ActionRow(
            stringResource(R.string.admin_unlocked_desc),
            stringResource(R.string.admin_lock),
            Icons.Rounded.Lock,
            onLock,
        )
        LinkRow(stringResource(R.string.admin_change)) { onOpenPin(PinMode.CHANGE) }
        LinkRow(stringResource(R.string.admin_handover)) { onOpenPin(PinMode.HANDOVER) }
    }
    MutedText(stringResource(R.string.admin_log_title), modifier = Modifier.padding(top = SECOND_GROUP_EXTRA_GAP))
    Column {
        if (recentEdits.isEmpty()) {
            SettingRow { RowLabel(stringResource(R.string.admin_log_empty)) }
        } else {
            recentEdits.forEach { edit -> AdminLogRow(edit, people) }
        }
    }
}

/** 관리자 수정 기록 한 줄: "10.13 (화) 운행 · 분류 업무 → 개인" + 고친 시각 */
@Composable
private fun AdminLogRow(edit: AdminEdit, people: List<Person>) {
    val field = when (edit.field) {
        AdminEdit.Field.CATEGORY -> stringResource(R.string.admin_log_field_category)
        AdminEdit.Field.PERSON -> stringResource(R.string.admin_log_field_person)
    }
    val text = stringResource(
        R.string.admin_log_row,
        Formatters.day(edit.tripDate),
        field,
        editValueName(edit.field, edit.fromValue, people),
        editValueName(edit.field, edit.toValue, people),
    )
    SettingRow {
        RowLabel(text)
        Text(Formatters.time(edit.editedAt.toLocalTime()), fontWeight = FontWeight.Bold)
    }
}

/**
 * 수정 기록 값(분류 이름·사람 id 글자)을 화면 이름으로. null 은 분류 대기·미지정.
 * 사람을 다시 등록해 id 가 사라졌으면 미지정으로 보인다.
 */
@Composable
private fun editValueName(field: AdminEdit.Field, value: String?, people: List<Person>): String = when (field) {
    AdminEdit.Field.CATEGORY -> categoryName(value)
    AdminEdit.Field.PERSON ->
        editedPerson(value, people)?.let { personName(it) } ?: stringResource(R.string.sheet_user_none)
}

// 저장된 분류 이름이 지금 enum 에 없으면(이름을 바꾼 옛 기록) 화면이 죽지 않게 원래 글자를 그대로 보인다
@Composable
private fun categoryName(value: String?): String {
    if (value == null) return stringResource(R.string.category_pending)
    val category = editedCategory(value) ?: return value
    return stringResource(category.labelRes)
}

/** 분류 수정 기록 값(enum 이름)의 분류. 모르는 이름이면 null */
internal fun editedCategory(value: String): Category? = Category.entries.firstOrNull { it.name == value }

/** 사람 수정 기록 값(id 글자)의 사람. 미지정이었거나 다시 등록해 id 가 사라졌으면 null */
internal fun editedPerson(value: String?, people: List<Person>): Person? {
    val id = value?.toLongOrNull() ?: return null
    return people.firstOrNull { it.id == id }
}
