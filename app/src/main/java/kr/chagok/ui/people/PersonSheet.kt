package kr.chagok.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.chagok.R
import kr.chagok.domain.ChagokConfig
import kr.chagok.ui.components.ActionButton
import kr.chagok.ui.components.ButtonStyle
import kr.chagok.ui.components.MutedText
import kr.chagok.ui.components.SheetOverlay
import kr.chagok.ui.components.TextInput
import kr.chagok.ui.components.personName
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kr.chagok.ui.theme.TouchSizes

/**
 * 사람 시트: 서식 ④사용자 성명·부서를 넣는다. 첫 실행에서 이름 없이 넣은 사람도 여기서 고친다.
 * 차 안 입력은 정차 중에만 받으므로 P단이 아니면 껍데기가 닫고, 넣던 값은 버린다. 관리자 잠금이 걸려도 닫는다.
 *
 * @param target 고칠 사람 id 또는 [PersonSheetTarget.NEW]
 */
@Composable
fun PersonSheet(target: Long, onDismiss: () -> Unit) {
    val viewModel: PersonSheetViewModel = viewModel(factory = PersonSheetViewModel.Factory)
    DisposableEffect(target) {
        viewModel.open(target)
        onDispose { viewModel.close() }
    }
    val state = viewModel.state.collectAsStateWithLifecycle().value ?: return
    if (!state.canManage || state.form.isGone) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val form = state.form
    val focus = LocalFocusManager.current

    SheetOverlay(
        onDismiss = onDismiss,
        header = {
            Text(
                stringResource(if (form.isNew) R.string.person_add else R.string.person_edit),
                fontSize = TextSizes.title,
                fontWeight = FontWeight.Bold,
            )
        },
    ) {
        TextInput(
            label = stringResource(R.string.person_name_label),
            value = form.name,
            onValueChange = viewModel::editName,
            placeholder = stringResource(R.string.person_name_hint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        if (form.name.isBlank()) MutedText(stringResource(R.string.person_name_blank))
        TextInput(
            label = stringResource(R.string.person_dept_label),
            value = form.dept,
            onValueChange = viewModel::editDept,
            placeholder = stringResource(R.string.person_dept_hint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )
        ActionButton(
            text = stringResource(R.string.person_save),
            onClick = {
                focus.clearFocus()
                viewModel.save(onSaved = onDismiss)
            },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            icon = Icons.Rounded.Check,
            style = ButtonStyle.Primary,
            enabled = !(form.isNew && state.isFull),
        )
        if (!form.isNew) {
            RemovePart(
                state = state,
                onAskRemove = viewModel::askRemove,
                onCancelRemove = viewModel::cancelRemove,
                onRemove = { viewModel.remove(onDone = onDismiss) },
                onUnhide = { viewModel.unhide(onDone = onDismiss) },
            )
        }
    }
}

/** 고치는 사람 아래: 지우기(한 번 더 묻기) · 숨긴 사람이면 다시 쓰기 */
@Composable
private fun RemovePart(
    state: PersonSheetUiState,
    onAskRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    onRemove: () -> Unit,
    onUnhide: () -> Unit,
) {
    val form = state.form
    val person = form.person ?: return
    when {
        person.isHidden -> {
            MutedText(stringResource(R.string.person_hidden_note))
            if (state.isFull) MutedText(stringResource(R.string.settings_people_full, ChagokConfig.PEOPLE_MAX))
            ActionButton(
                text = stringResource(R.string.person_unhide),
                onClick = onUnhide,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.AutoMirrored.Rounded.Undo,
                enabled = !state.isFull,
            )
        }
        form.isConfirmingRemove -> {
            val name = personName(person)
            Text(
                stringResource(if (form.isUsed) R.string.person_hide_confirm else R.string.person_remove_confirm, name),
                color = ChagokColors.pendingText,
                fontWeight = FontWeight.SemiBold,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TouchSizes.gap)) {
                ActionButton(
                    text = stringResource(R.string.person_remove_cancel),
                    onClick = onCancelRemove,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = stringResource(if (form.isUsed) R.string.person_hide_do else R.string.person_remove_do),
                    onClick = onRemove,
                    modifier = Modifier.weight(1f),
                    icon = if (form.isUsed) Icons.Rounded.VisibilityOff else Icons.Rounded.DeleteOutline,
                    style = ButtonStyle.Primary,
                )
            }
        }
        else -> {
            if (!state.canRemove) MutedText(stringResource(R.string.person_remove_last))
            ActionButton(
                text = stringResource(R.string.person_remove),
                onClick = onAskRemove,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.DeleteOutline,
                enabled = state.canRemove,
            )
        }
    }
}
