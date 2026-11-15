package kr.chagok.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.chagok.AppContainer
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.model.Person
import kr.chagok.ui.WhileUiSubscribed
import kr.chagok.ui.containerFactory
import kr.chagok.ui.launchSaving

/**
 * 사람 시트 상태. 시트가 열릴 때 저장된 값을 한 번 읽어 들고 있다가 "저장"을 눌러야 저장한다.
 * 지난 운행에 나오는 사람은 지우지 않고 숨긴다 (기록부 ④성명이 비면 안 된다). PIN 을 정한 차는 관리자만 고친다.
 */
class PersonSheetViewModel(private val container: AppContainer) : ViewModel() {
    private val form = MutableStateFlow<PersonForm?>(null)
    private var opening: Job? = null

    // 저장이 끝나 시트가 닫히기 전에 또 누르면 같은 사람이 두 번 더해진다. 하나가 도는 동안은 무시한다
    private var saving: Job? = null

    // 시트를 열고 닫을 때마다 는다. 저장 중에 시트를 닫고 다른 시트를 열었으면, 늦게 끝난 저장이 새 시트를 닫지 않게 한다
    private var session = 0

    val state: StateFlow<PersonSheetUiState?> = combine(
        form,
        container.people.people,
        container.admin.state,
    ) { form, people, admin ->
        form?.let { PersonSheetUiState(form = it, visibleCount = people.size, canManage = admin.canManage) }
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    /** 시트가 열릴 때. [target] 이 [PersonSheetTarget.NEW] 면 새 사람, 아니면 그 id 의 사람 */
    fun open(target: Long) {
        session++
        opening?.cancel()
        form.value = null
        if (target == PersonSheetTarget.NEW) {
            form.value = PersonForm()
            return
        }
        opening = viewModelScope.launch {
            val person = container.people.find(target)
            form.value = if (person == null) {
                PersonForm(isGone = true)
            } else {
                PersonForm(
                    person = person,
                    name = person.name,
                    dept = person.dept,
                    isUsed = container.people.isUsed(person.id),
                )
            }
        }
    }

    /** 시트가 사라지면 넣던 값을 버린다 (다음에 열면 저장된 값부터) */
    fun close() {
        session++
        opening?.cancel()
        form.value = null
    }

    fun editName(text: String) = form.update { it?.copy(name = text.take(ChagokConfig.PERSON_TEXT_MAX)) }

    fun editDept(text: String) = form.update { it?.copy(dept = text.take(ChagokConfig.PERSON_TEXT_MAX)) }

    /** 저장이 끝나면 [onSaved] 로 시트를 닫는다. 그사이 시트가 닫혔으면 부르지 않고, 실패하면 시트를 그대로 둔다 */
    fun save(onSaved: () -> Unit) {
        if (saving?.isActive == true) return
        val current = form.value ?: return
        val ui = state.value ?: return
        if (!canManage() || (current.isNew && ui.isFull)) return
        // 다시 하기는 권한·인원을 다시 보도록 같은 함수로 한다. 그사이 다른 시트가 열렸으면 그 값을 저장하지 않는다
        val opened = session
        saving = launchSaving(container.messages, retry = { if (session == opened) save(onSaved) }) {
            val person = current.person
            if (person == null) {
                container.people.add(current.name, current.dept)
            } else {
                container.people.update(person.id, current.name, current.dept)
            }
            if (session == opened) onSaved()
        }
    }

    /** "지우기": 바로 지우지 않고 한 번 더 묻는다 (지울지 숨길지 알려 준다) */
    fun askRemove() {
        if (state.value?.canRemove != true) return
        form.update { it?.copy(isConfirmingRemove = true) }
    }

    fun cancelRemove() = form.update { it?.copy(isConfirmingRemove = false) }

    fun remove(onDone: () -> Unit) {
        if (saving?.isActive == true) return
        val person = form.value?.person ?: return
        if (!canManage() || state.value?.canRemove != true) return
        val opened = session
        saving = launchSaving(container.messages, retry = { if (session == opened) remove(onDone) }) {
            container.people.remove(person.id)
            if (session == opened) onDone()
        }
    }

    /** 숨긴 사람을 다시 "누가 타나요"에 */
    fun unhide(onDone: () -> Unit) {
        if (saving?.isActive == true) return
        val person = form.value?.person ?: return
        // 다시 쓰면 보이는 사람이 하나 는다. 꽉 찼으면 막는다 (설정의 사람 추가와 같은 상한)
        if (!canManage() || !person.isHidden || state.value?.isFull != false) return
        val opened = session
        saving = launchSaving(container.messages, retry = { if (session == opened) unhide(onDone) }) {
            container.people.unhide(person.id)
            if (session == opened) onDone()
        }
    }

    // 시트를 열 때 관리자인지 봤지만, 그사이 잠긴 경우까지 한 번 더 막는다
    private fun canManage(): Boolean = container.admin.state.value.canManage

    companion object {
        val Factory = containerFactory(::PersonSheetViewModel)
    }
}

/** 사람 시트를 무엇으로 여는지. 고칠 사람은 그 id, 새 사람은 [NEW] (Room id 는 1부터라 겹치지 않는다) */
object PersonSheetTarget {
    const val NEW = 0L
}

/**
 * @property person 고치는 사람. null 이면 새 사람
 * @property isUsed 지난 운행·수정 기록에 나와서 지우면 숨겨지는 사람
 * @property isGone 열려던 사람이 그사이 지워졌다 (시트를 닫는다)
 */
data class PersonForm(
    val person: Person? = null,
    val name: String = "",
    val dept: String = "",
    val isUsed: Boolean = false,
    val isConfirmingRemove: Boolean = false,
    val isGone: Boolean = false,
) {
    val isNew: Boolean get() = person == null
}

/** @property visibleCount 지금 쓰는(숨기지 않은) 사람 수 */
data class PersonSheetUiState(
    val form: PersonForm,
    val visibleCount: Int,
    val canManage: Boolean,
) {
    /** 더 넣을 수 없다 */
    val isFull: Boolean get() = visibleCount >= ChagokConfig.PEOPLE_MAX

    /** 지울(숨길) 수 있는지: 쓰는 사람이 한 명은 남아야 한다 (혼자 쓰는 차의 기본 사용자) */
    val canRemove: Boolean get() = form.person?.isHidden == false && visibleCount > 1
}
