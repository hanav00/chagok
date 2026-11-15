package kr.chagok.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.chagok.AppContainer
import kr.chagok.domain.VehicleRules
import kr.chagok.domain.model.VehicleField
import kr.chagok.domain.model.VehicleInfo
import kr.chagok.ui.WhileUiSubscribed
import kr.chagok.ui.containerFactory
import kr.chagok.ui.launchSaving

/**
 * 차량·사업자 시트 상태: 서식 머리 값(법인명·사업자등록번호·차종·번호판)을 넣는다.
 * 시트가 열릴 때 저장된 값을 한 번 읽어 고칠 거리로 들고 있다가, "저장"을 눌러야 저장한다.
 * PIN 을 정한 차는 관리자만 저장한다 (기록 방식과 같이 문서 머리도 관리자 몫).
 */
class VehicleInfoViewModel(private val container: AppContainer) : ViewModel() {
    private val form = MutableStateFlow<VehicleForm?>(null)
    private var opening: Job? = null

    // 저장이 끝나 시트가 닫히기 전에 또 누르면 같은 값을 두 번 저장한다. 하나가 도는 동안은 무시한다
    private var saving: Job? = null

    // 시트를 열고 닫을 때마다 올린다. 저장 중에 시트를 닫고 다시 열었으면 늦게 끝난 저장이 새 시트를 닫지 않게 한다
    private var session = 0

    val state: StateFlow<VehicleInfoUiState?> = combine(form, container.admin.state) { form, admin ->
        form?.let { VehicleInfoUiState(form = it, canManage = admin.canManage) }
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    /** 시트가 열릴 때. [scope] 가 빠진 값만이면 지금 빠진 칸만 묻는다 (넣는 동안 칸이 사라지지 않게 여기서 정해 둔다) */
    fun open(scope: VehicleSheetScope) {
        session++
        opening?.cancel()
        form.value = null
        opening = viewModelScope.launch {
            val saved = container.settings.settings.first().vehicle
            val missing = VehicleRules.missingFields(saved)
            val fields =
                if (scope == VehicleSheetScope.MISSING && missing.isNotEmpty()) missing else VehicleField.entries
            // 사업자등록번호는 고치는 동안 숫자만 들고 있다 (대시는 보여 줄 때만)
            val draft = saved.copy(bizNo = VehicleRules.bizNoDigits(saved.bizNo))
            form.value = VehicleForm(fields = fields, draft = draft)
        }
    }

    /** 시트가 사라지면 넣던 값을 버린다 (다음에 열면 저장된 값부터) */
    fun close() {
        session++
        opening?.cancel()
        form.value = null
    }

    fun edit(field: VehicleField, text: String) {
        form.update { current ->
            current?.copy(draft = VehicleRules.withValue(current.draft, field, VehicleRules.cleanInput(field, text)))
        }
    }

    /** 저장이 끝나면 [onSaved] 로 시트를 닫는다 (그사이 닫혔으면 부르지 않는다). 실패하면 알림만 뜨고 시트는 그대로 */
    fun save(onSaved: () -> Unit) {
        if (saving?.isActive == true) return
        val current = form.value ?: return
        if (!current.isValid || !canManage()) return
        val opened = session
        // 다시 하기는 같은 함수로 해서 그사이 더 고친 값을 저장하고 잠금도 다시 본다. 다른 시트가 열렸으면 하지 않는다
        saving = launchSaving(container.messages, retry = { if (session == opened) save(onSaved) }) {
            container.settings.setVehicleInfo(VehicleRules.normalized(current.draft))
            if (session == opened) onSaved()
        }
    }

    // 시트를 열 때 관리자인지 봤지만, 그사이 잠긴 경우까지 한 번 더 막는다
    private fun canManage(): Boolean = container.admin.state.value.canManage

    companion object {
        val Factory = containerFactory(::VehicleInfoViewModel)
    }
}

/** 어디서 열었는지: 설정은 모든 칸, 내보내기의 "빠진 값 채우기"는 빠진 칸만 */
enum class VehicleSheetScope { ALL, MISSING }

/**
 * @property fields 시트에 보일 칸 (서식 머리 순서)
 * @property draft 고치는 중인 값. 사업자등록번호는 숫자만
 */
data class VehicleForm(
    val fields: List<VehicleField>,
    val draft: VehicleInfo,
) {
    /** 저장할 수 있는지: 지금은 사업자등록번호를 몇 자리만 넣은 경우만 막는다 */
    val isValid: Boolean get() = VehicleRules.isBizNoValid(draft.bizNo)
}

data class VehicleInfoUiState(
    val form: VehicleForm,
    val canManage: Boolean,
)
