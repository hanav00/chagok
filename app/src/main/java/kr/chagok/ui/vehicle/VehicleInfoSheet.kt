package kr.chagok.ui.vehicle

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.chagok.R
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.VehicleRules
import kr.chagok.domain.model.VehicleField
import kr.chagok.ui.components.ActionButton
import kr.chagok.ui.components.ButtonStyle
import kr.chagok.ui.components.SheetOverlay
import kr.chagok.ui.components.TextInput
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

/**
 * 차량·사업자 시트: 서식 머리 값(법인명·사업자등록번호·차종·번호판)을 넣는다.
 * 껍데기가 띄우는 시트라 P단이 아니면 저절로 닫히고(차 안 입력은 정차 중에만) 넣던 값은 버린다.
 * PIN 을 정한 차는 관리자만 연다. 여는 쪽이 PIN 을 먼저 받고, 그사이 잠기면 시트를 닫는다.
 */
@Composable
fun VehicleInfoSheet(scope: VehicleSheetScope, onDismiss: () -> Unit) {
    val viewModel: VehicleInfoViewModel = viewModel(factory = VehicleInfoViewModel.Factory)
    DisposableEffect(scope) {
        viewModel.open(scope)
        onDispose { viewModel.close() }
    }
    val state = viewModel.state.collectAsStateWithLifecycle().value ?: return
    if (!state.canManage) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val form = state.form
    val focus = LocalFocusManager.current

    SheetOverlay(
        onDismiss = onDismiss,
        header = {
            Text(
                stringResource(R.string.settings_vehicle_title),
                fontSize = TextSizes.title,
                fontWeight = FontWeight.Bold,
            )
        },
    ) {
        form.fields.forEachIndexed { index, field ->
            val isLast = index == form.fields.lastIndex
            val input = field.input
            TextInput(
                label = stringResource(field.labelRes),
                value = VehicleRules.valueOf(form.draft, field),
                onValueChange = { viewModel.edit(field, it) },
                placeholder = stringResource(input.placeholder),
                error = if (field == VehicleField.BIZ_NO && !form.isValid) {
                    stringResource(R.string.vehicle_biz_no_error, ChagokConfig.BIZ_NO_LENGTH)
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = input.keyboard,
                    imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                visualTransformation =
                    if (field == VehicleField.BIZ_NO) BizNoTransformation else VisualTransformation.None,
            )
        }
        ActionButton(
            text = stringResource(R.string.vehicle_save),
            onClick = {
                focus.clearFocus()
                viewModel.save(onSaved = onDismiss)
            },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            icon = Icons.Rounded.Check,
            style = ButtonStyle.Primary,
            enabled = form.isValid,
        )
    }
}

/** 칸마다 예시·키보드 (이름은 [labelRes]) */
private class FieldInput(@StringRes val placeholder: Int, val keyboard: KeyboardType)

private val VehicleField.input: FieldInput
    get() = when (this) {
        VehicleField.COMPANY -> FieldInput(R.string.vehicle_hint_company, KeyboardType.Text)
        VehicleField.BIZ_NO -> FieldInput(R.string.vehicle_hint_biz_no, KeyboardType.Number)
        VehicleField.MODEL -> FieldInput(R.string.vehicle_hint_model, KeyboardType.Text)
        VehicleField.PLATE -> FieldInput(R.string.vehicle_hint_plate, KeyboardType.Text)
    }
