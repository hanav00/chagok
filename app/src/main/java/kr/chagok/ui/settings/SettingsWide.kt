package kr.chagok.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import kr.chagok.R
import kr.chagok.ui.admin.PinMode
import kr.chagok.ui.components.WideScreenPadding
import kr.chagok.ui.components.SheetOverlay
import kr.chagok.ui.components.WideSideWidth
import kr.chagok.ui.components.departureLabelRes
import kr.chagok.ui.components.personName
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

/**
 * 넓은 창 설정. 왼쪽(운전석 쪽)은 이 차와 관리자, 오른쪽은 항목마다 지금 값 한 줄.
 * 줄을 누르면 그 항목만 시트로 연다. 시트는 이 화면 안에 띄워서 PIN 시트가 그 위로 올라와도 닫히지 않는다.
 */
@Composable
internal fun SettingsWide(
    ui: SettingsUiState,
    viewModel: SettingsViewModel,
    onOpenPin: (PinMode) -> Unit,
    onOpenTutorial: () -> Unit,
    partContent: @Composable (SettingsPart) -> Unit,
) {
    var openPart by rememberSaveable { mutableStateOf<SettingsPart?>(null) }
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxSize().padding(WideScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.wideColumnGap),
        ) {
            Column(Modifier.width(WideSideWidth).fillMaxHeight().verticalScroll(rememberScrollState())) {
                ThisCar(ui)
                AdminSection(ui.admin, ui.recentEdits, ui.people, onOpenPin = onOpenPin, onLock = viewModel::lockAdmin)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                PartRow(SettingsPart.RECORDING, recordingValue(ui)) { openPart = it }
                PartRow(SettingsPart.USERS, usersValue(ui)) { openPart = it }
                PartRow(SettingsPart.PLACES, placesValue(ui)) { openPart = it }
                PartRow(SettingsPart.VEHICLE, vehicleValue(ui)) { openPart = it }
                ToggleRow(
                    label = stringResource(R.string.settings_voice_label),
                    isOn = ui.settings.isTripVoiceOn,
                    onToggle = { viewModel.setTripVoiceOn(!ui.settings.isTripVoiceOn) },
                )
                if (ui.isGleoAvailable) PartRow(SettingsPart.GLEO, value = "") { openPart = it }
                SettingRow(onClick = onOpenTutorial) {
                    Text(
                        stringResource(R.string.settings_tutorial_replay),
                        Modifier.weight(1f),
                        fontSize = TextSizes.subtitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                    EditChevron()
                }
                PartRow(SettingsPart.PRIVACY, value = "") { openPart = it }
            }
        }
        openPart?.let { part ->
            SheetOverlay(
                onDismiss = { openPart = null },
                header = { Text(stringResource(part.titleRes), fontSize = TextSizes.title, fontWeight = FontWeight.Bold) },
            ) {
                partContent(part)
            }
        }
    }
}

/** 이 차: 차종 · 번호판을 크게, 법인과 사업연도는 줄로 */
@Composable
private fun ThisCar(ui: SettingsUiState) {
    val vehicle = ui.settings.vehicle
    Text(stringResource(R.string.settings_this_car), color = ChagokColors.textSub)
    Text(
        vehicle.model.ifBlank { stringResource(R.string.form_missing) },
        Modifier.padding(top = Spacing.xs),
        fontSize = TextSizes.hero,
        fontWeight = FontWeight.ExtraBold,
    )
    if (vehicle.plate.isNotBlank()) {
        Text(vehicle.plate, Modifier.padding(top = Spacing.xxs), color = ChagokColors.textSub, fontSize = TextSizes.subtitle, fontWeight = FontWeight.Bold)
    }
    Column(Modifier.padding(top = Spacing.xl, bottom = Spacing.xxl)) {
        InfoRow(stringResource(R.string.settings_company), vehicle.company)
        InfoRow(
            stringResource(R.string.settings_fiscal_year),
            stringResource(
                R.string.form_fiscal_range,
                Formatters.periodMonth(ui.fiscalYear.start),
                Formatters.periodMonth(ui.fiscalYear.end),
            ),
        )
    }
}

@Composable
private fun PartRow(part: SettingsPart, value: String, onOpen: (SettingsPart) -> Unit) {
    SettingRow(onClick = { onOpen(part) }) {
        Text(stringResource(part.titleRes), Modifier.weight(1f), fontSize = TextSizes.subtitle, fontWeight = FontWeight.SemiBold)
        if (value.isNotBlank()) Text(value, color = ChagokColors.textSub, maxLines = 1)
        EditChevron()
    }
}

@Composable
private fun recordingValue(ui: SettingsUiState): String =
    stringResource(ui.settings.defaultCategory.departureLabelRes) + Formatters.SEPARATOR +
        stringResource(ui.settings.usage.labelRes)

@Composable
private fun usersValue(ui: SettingsUiState): String =
    ui.people.filterNot { it.isHidden }.map { personName(it) }.joinToString(Formatters.SEPARATOR)

@Composable
private fun placesValue(ui: SettingsUiState): String {
    val kinds = listOfNotNull(
        ui.settings.home?.let { stringResource(R.string.place_kind_home) },
        ui.settings.office?.let { stringResource(R.string.place_kind_office) },
    )
    return kinds.joinToString(Formatters.SEPARATOR).ifBlank { stringResource(R.string.settings_places_none) }
}

private fun vehicleValue(ui: SettingsUiState): String {
    val vehicle = ui.settings.vehicle
    return listOf(vehicle.model, vehicle.company).filter { it.isNotBlank() }.joinToString(Formatters.SEPARATOR)
}
