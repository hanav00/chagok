package kr.chagok.ui.table

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kr.chagok.AppContainer
import kr.chagok.domain.model.AdminState
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.Trip
import kr.chagok.ui.WhileUiSubscribed
import kr.chagok.ui.containerFactory
import kr.chagok.ui.launchTripDictation
import kr.chagok.ui.launchSaving

/**
 * 운행 상세 시트 상태. 기록부·내보내기에서 운행 하나를 열어 분류를 고친다.
 * 관리자 모드에서 고친 것은 "관리자 수정"으로 남는다. 관리자 여부는 누른 그 순간의 상태로 정한다 (다시 하기도 같다).
 */
class TripSheetViewModel(private val container: AppContainer) : ViewModel() {
    private val tripId = MutableStateFlow<Long?>(null)
    private val _isListening = MutableStateFlow(false)

    /** 마이크로 듣는 중 */
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    // 사람을 고를 수 있는지 정하는 두 값을 묶는다 (combine 은 다섯 개까지라)
    private val access = combine(container.settings.settings, container.admin.state) { settings, admin ->
        SheetAccess(isSharedCar = settings.usage.isShared, admin = admin)
    }

    val state: StateFlow<TripSheetUiState?> = combine(
        tripId.filterNotNull(),
        container.trips.trips,
        container.trips.restorableTripId,
        container.people.allPeople,
        access,
    ) { id, trips, restorableId, allPeople, access ->
        val trip = trips.firstOrNull { it.id == id }
        TripSheetUiState(
            tripId = id,
            trip = trip,
            // 숨긴 사람도 지난 운행에는 이름이 보이고, 고르는 얼굴에서만 빠진다
            person = allPeople.firstOrNull { it.id == trip?.personId },
            people = allPeople.filterNot(Person::isHidden),
            isPersonPickable = trip != null && isPersonPickable(trip, access),
            // 기록부 ④칸과 같은 규칙: 혼자 쓰는 차·기록 안 된 주행은 사람을 묻지 않으니 비어 있어도 "미지정"이 아니다
            isUnassigned = trip != null && access.isSharedCar && trip.isUnassigned,
            isPlaceRestorable = restorableId == id,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    fun show(id: Long) {
        tripId.value = id
    }

    fun classify(category: Category) {
        val id = tripId.value ?: return
        val isAdminEdit = isAdminEdit()
        launchSaving(container.messages) { container.trips.classify(id, category, isAdminEdit) }
    }

    fun assignPerson(personId: Long) {
        val id = tripId.value ?: return
        val isAdminEdit = isAdminEdit()
        launchSaving(container.messages) { container.trips.assignPerson(id, personId, isAdminEdit) }
    }

    /** 마이크로 방문 목적을 시트에서 다시 말하거나 말로 분류·사람을 고친다 (방금 운행 카드를 닫았거나 지난 운행) */
    fun dictate() {
        val id = tripId.value ?: return
        launchTripDictation(
            container,
            id,
            isListening = { _isListening.value },
            setListening = { _isListening.value = it },
        )
    }

    /** 시트를 닫으면 이 운행을 개인으로 바꾸며 지운 장소를 확정한다 (그 뒤로는 업무로 바꿔도 장소가 안 돌아온다) */
    fun commitPlaceDeletion(tripId: Long) = container.trips.commitPlaceDeletion(tripId)

    // 다시 하기도 처음 누른 때의 값으로 저장한다. 그사이 잠겼다고 관리자 수정이 일반 수정으로 바뀌면 안 된다
    private fun isAdminEdit(): Boolean = container.admin.state.value.isAdminEdit

    // 공용차에서 미지정이면 누구나, 이미 정해졌으면 관리 권한이 있을 때만. 기록 안 된 주행은 묻지 않는다
    private fun isPersonPickable(trip: Trip, access: SheetAccess): Boolean =
        access.isSharedCar && !trip.isGap && (trip.isUnassigned || access.admin.canManage)

    private data class SheetAccess(val isSharedCar: Boolean, val admin: AdminState)

    companion object {
        val Factory = containerFactory(::TripSheetViewModel)
    }
}

data class TripSheetUiState(
    /** 이 상태가 어느 운행을 보고 만든 것인지 (시트가 다른 운행으로 바뀌는 사이의 낡은 상태를 거른다) */
    val tripId: Long,
    /** null 이면 운행이 사라졌다 (시트를 닫는다) */
    val trip: Trip?,
    val person: Person?,
    val people: List<Person>,
    val isPersonPickable: Boolean,
    /** 공용차에서 사람을 아직 안 골랐다 (사용자 칸 "미지정") */
    val isUnassigned: Boolean,
    /** 개인으로 바꾸며 지운 장소를 아직 되살릴 수 있다 ("닫으면 장소를 지워요") */
    val isPlaceRestorable: Boolean,
)
