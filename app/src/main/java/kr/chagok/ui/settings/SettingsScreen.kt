package kr.chagok.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.chagok.R
import kr.chagok.domain.FiscalYear
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.VehicleInfo
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.ui.admin.PinMode
import kr.chagok.ui.components.GleoExampleList
import kr.chagok.ui.components.HeaderButton
import kr.chagok.ui.components.Hint
import kr.chagok.ui.components.LockedLine
import kr.chagok.ui.components.ScreenColumn
import kr.chagok.ui.components.Section
import kr.chagok.ui.components.SectionTitle
import kr.chagok.ui.components.SegmentedControl
import kr.chagok.ui.components.departureLabelRes
import kr.chagok.ui.format.Formatters

/**
 * 설정 화면. 개인 운행 위치를 남기지 않는 것·분류 변경 이력은 스위치 없이 글로만 적는다.
 * 끌 수 있으면 세무 소명 때 기록을 믿기 어렵다. 잠긴 차에서 시트를 열려면 PIN 부터 받는다.
 *
 * @param onOpenPin 관리자 PIN 시트 열기. 둘째 인자는 PIN 이 맞은 뒤 할 일
 * @param onEditPerson 사람 id 또는 [kr.chagok.ui.people.PersonSheetTarget.NEW]
 * @param onOpenTutorial 첫 실행 알아보기(이렇게 기록돼요 · 말해 보기)를 다시 보기
 */
@Composable
fun SettingsScreen(
    isWide: Boolean,
    onOpenPin: (PinMode, () -> Unit) -> Unit,
    onEditVehicle: () -> Unit,
    onEditPerson: (Long) -> Unit,
    onOpenTutorial: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ui = state ?: return
    val part = @Composable { part: SettingsPart ->
        SettingsPartContent(part, ui, viewModel, onOpenPin, onEditVehicle, onEditPerson, isTitled = !isWide)
    }
    if (isWide) {
        SettingsWide(ui, viewModel, onOpenPin = { onOpenPin(it) {} }, onOpenTutorial = onOpenTutorial, partContent = part)
        return
    }
    val adminSection = @Composable {
        AdminSection(ui.admin, ui.recentEdits, ui.people, onOpenPin = { onOpenPin(it) {} }, onLock = viewModel::lockAdmin)
    }
    ScreenColumn {
        if (ui.isAdminFirst) adminSection()
        part(SettingsPart.RECORDING)
        part(SettingsPart.VEHICLE)
        part(SettingsPart.USERS)
        part(SettingsPart.PLACES)
        NoticeSection(
            isVoiceOn = ui.settings.isTripVoiceOn,
            onToggleVoice = { viewModel.setTripVoiceOn(!ui.settings.isTripVoiceOn) },
        )
        if (ui.isGleoAvailable) part(SettingsPart.GLEO)
        TutorialSection(onOpenTutorial)
        if (!ui.isAdminFirst) adminSection()
        part(SettingsPart.PRIVACY)
    }
}

/** 설정 묶음. 좁은 창은 차례로 늘어놓고, 넓은 창은 한 줄씩 보이다가 누르면 시트로 연다 */
internal enum class SettingsPart(@StringRes val titleRes: Int) {
    RECORDING(R.string.settings_recording_title),
    VEHICLE(R.string.settings_vehicle_title),
    USERS(R.string.settings_users_title),
    PLACES(R.string.settings_places_title),
    GLEO(R.string.settings_gleo_title),
    PRIVACY(R.string.settings_privacy_title),
}

/** 묶음 하나. 잠긴 차에서 시트를 열려면 PIN 부터 받고, 맞으면 하려던 일을 잇는다 */
@Composable
private fun SettingsPartContent(
    part: SettingsPart,
    ui: SettingsUiState,
    viewModel: SettingsViewModel,
    onOpenPin: (PinMode, () -> Unit) -> Unit,
    onEditVehicle: () -> Unit,
    onEditPerson: (Long) -> Unit,
    isTitled: Boolean,
) {
    val isLocked = !ui.admin.canManage
    val unlock = { onOpenPin(PinMode.UNLOCK) {} }
    when (part) {
        SettingsPart.RECORDING -> RecordingSection(
            settings = ui.settings,
            isLocked = isLocked,
            isTitled = isTitled,
            onUnlock = unlock,
            onSelectDefaultCategory = viewModel::setDefaultCategory,
            onSelectUsage = viewModel::setUsage,
        )
        SettingsPart.VEHICLE -> VehicleSection(
            vehicle = ui.settings.vehicle,
            fiscalYear = ui.fiscalYear,
            isLocked = isLocked,
            isTitled = isTitled,
            onUnlock = unlock,
            onEdit = { if (isLocked) onOpenPin(PinMode.UNLOCK, onEditVehicle) else onEditVehicle() },
        )
        SettingsPart.USERS -> UsersSection(
            people = ui.people,
            isLocked = isLocked,
            isTitled = isTitled,
            onUnlock = unlock,
            onEdit = { id -> if (isLocked) onOpenPin(PinMode.UNLOCK) { onEditPerson(id) } else onEditPerson(id) },
            onMoveUp = viewModel::movePersonUp,
        )
        SettingsPart.PLACES -> PlacesSection(
            settings = ui.settings,
            isLocked = isLocked,
            isTitled = isTitled,
            onUnlock = unlock,
            onSync = viewModel::syncPlacesFromNavi,
            onHere = viewModel::setPlaceHere,
        )
        SettingsPart.GLEO -> GleoSection(isShared = ui.settings.usage.isShared, isTitled = isTitled)
        SettingsPart.PRIVACY -> PrivacySection(isTitled = isTitled)
    }
}

/** 기록 방식: 시동마다 쓰는 기본 분류와 누가 쓰는 차. PIN 이 있으면 관리자만 바꾼다 (지금 값은 보인다) */
@Composable
private fun RecordingSection(
    settings: ChagokSettings,
    isLocked: Boolean,
    isTitled: Boolean,
    onUnlock: () -> Unit,
    onSelectDefaultCategory: (Category) -> Unit,
    onSelectUsage: (VehicleUsage) -> Unit,
) {
    Section {
        if (isTitled) SectionTitle(stringResource(R.string.settings_recording_title))
        if (isLocked) LockedLine(stringResource(R.string.admin_locked_setting), onUnlock = onUnlock)
        GroupLabel(stringResource(R.string.settings_default_label))
        SegmentedControl(
            options = Category.DEPARTURE_CHOICES,
            selected = settings.defaultCategory,
            label = { stringResource(it.departureLabelRes) },
            onSelect = onSelectDefaultCategory,
            enabled = !isLocked,
        )
        GroupLabel(stringResource(R.string.settings_usage_label), Modifier.padding(top = SECOND_GROUP_EXTRA_GAP))
        SegmentedControl(
            options = VehicleUsage.entries,
            selected = settings.usage,
            label = { stringResource(it.labelRes) },
            onSelect = onSelectUsage,
            enabled = !isLocked,
        )
    }
}

@get:StringRes
internal val VehicleUsage.labelRes: Int
    get() = when (this) {
        VehicleUsage.SOLO -> R.string.settings_usage_solo
        VehicleUsage.SHARED -> R.string.settings_usage_shared
        VehicleUsage.CHAUFFEUR -> R.string.settings_usage_chauffeur
    }

/** 차량·사업자: SDK 로 읽는 건 차대번호뿐이라 나머지 서식 머리 값은 줄을 눌러 시트에서 넣는다 */
@Composable
private fun VehicleSection(
    vehicle: VehicleInfo,
    fiscalYear: FiscalYear,
    isLocked: Boolean,
    isTitled: Boolean,
    onUnlock: () -> Unit,
    onEdit: () -> Unit,
) {
    Section {
        if (isTitled) SectionTitle(stringResource(R.string.settings_vehicle_title))
        if (isLocked) LockedLine(stringResource(R.string.admin_locked_setting), onUnlock = onUnlock)
        Column {
            InfoRow(stringResource(R.string.settings_vin), vehicle.vin)
            InfoRow(stringResource(R.string.settings_model), vehicle.model, onEdit = onEdit)
            InfoRow(stringResource(R.string.settings_plate), vehicle.plate, onEdit = onEdit)
            InfoRow(stringResource(R.string.settings_company), vehicle.company, onEdit = onEdit)
            InfoRow(stringResource(R.string.settings_biz_no), vehicle.bizNo, onEdit = onEdit)
            InfoRow(
                stringResource(R.string.settings_fiscal_year),
                stringResource(
                    R.string.form_fiscal_range,
                    Formatters.periodMonth(fiscalYear.start),
                    Formatters.periodMonth(fiscalYear.end),
                ),
            )
        }
    }
}

/**
 * 장소는 출퇴근을 나누는 데만 쓰므로 집·회사만 둔다 (거래처는 등록하지 않는다).
 * 내비 즐겨찾기로 맞추거나 지금 서 있는 곳으로 정한다.
 */
@Composable
private fun PlacesSection(
    settings: ChagokSettings,
    isLocked: Boolean,
    isTitled: Boolean,
    onUnlock: () -> Unit,
    onSync: () -> Unit,
    onHere: (isHome: Boolean) -> Unit,
) {
    val places = listOfNotNull(
        settings.home?.let { it.displayName to R.string.place_kind_home },
        settings.office?.let { it.displayName to R.string.place_kind_office },
    )
    Section {
        if (isTitled) SectionTitle(stringResource(R.string.settings_places_title))
        Column {
            if (places.isEmpty()) {
                SettingRow { RowLabel(stringResource(R.string.settings_places_none)) }
            }
            places.forEach { (name, kind) -> InfoRow(stringResource(kind), name) }
            if (isLocked) {
                LockedLine(stringResource(R.string.admin_locked_setting), onUnlock = onUnlock)
            } else {
                ActionRow(
                    description = stringResource(R.string.settings_places_sync_desc),
                    buttonText = stringResource(R.string.settings_places_sync),
                    icon = Icons.Rounded.Sync,
                    onClick = onSync,
                )
                SettingRow {
                    RowLabel(stringResource(R.string.settings_place_here_desc))
                    HeaderButton(
                        onClick = { onHere(true) },
                        icon = Icons.Rounded.Home,
                        text = stringResource(R.string.settings_place_home_here),
                    )
                    HeaderButton(
                        onClick = { onHere(false) },
                        icon = Icons.Rounded.Business,
                        text = stringResource(R.string.settings_place_office_here),
                    )
                }
            }
        }
    }
}

/** Gleo 로 말하기 예시 (첫 실행에서 본 것을 다시 본다). Gleo 가 없는 차에서는 칸이 없다 */
@Composable
private fun GleoSection(isShared: Boolean, isTitled: Boolean) {
    Section {
        if (isTitled) SectionTitle(stringResource(R.string.settings_gleo_title))
        GleoExampleList(isShared = isShared)
        Hint(stringResource(R.string.gleo_example_tip))
    }
}

/** 사용법: 첫 실행 알아보기를 다시 본다. 설정 단계는 위 항목에서 바로 바꾼다 */
@Composable
private fun TutorialSection(onOpen: () -> Unit) {
    Section {
        SectionTitle(stringResource(R.string.settings_tutorial_title))
        Column {
            SettingRow(onClick = onOpen) {
                RowLabel(stringResource(R.string.settings_tutorial_replay))
                EditChevron()
            }
        }
    }
}

@Composable
private fun NoticeSection(isVoiceOn: Boolean, onToggleVoice: () -> Unit) {
    Section {
        SectionTitle(stringResource(R.string.settings_notice_title))
        Column {
            ToggleRow(
                label = stringResource(R.string.settings_voice_label),
                isOn = isVoiceOn,
                onToggle = onToggleVoice,
            )
        }
    }
}

/** 개인정보 · 기록 원칙: 끌 수 없는 기본 동작이라 글로만 */
@Composable
private fun PrivacySection(isTitled: Boolean) {
    Section {
        if (isTitled) SectionTitle(stringResource(R.string.settings_privacy_title))
        Column {
            PRINCIPLES.forEach { label -> SettingRow { RowLabel(stringResource(label)) } }
        }
    }
}

private val PRINCIPLES = listOf(
    R.string.settings_principle_personal,
    R.string.settings_principle_two_points,
    R.string.settings_principle_history,
)

/** 세그먼트 위 이름표 ("기본 분류", "누가 쓰는 차") */
@Composable
private fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontWeight = FontWeight.SemiBold)
}
