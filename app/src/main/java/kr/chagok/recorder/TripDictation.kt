package kr.chagok.recorder

import kotlinx.coroutines.flow.first
import kr.chagok.car.VoiceInput
import kr.chagok.data.PeopleRepository
import kr.chagok.data.SettingsRepository
import kr.chagok.data.TripRepository
import kr.chagok.domain.PersonMatcher
import kr.chagok.domain.SpokenEdit
import kr.chagok.domain.model.AdminState
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.Trip

/**
 * 마이크 받아쓰기: 한 번 듣고 운행을 고친다. 홈 "방금 운행" 카드와 운행 상세 시트가 같이 쓴다.
 * "개인", "출퇴근이었어"는 분류를, "박정훈이 탔어"는 사람을 바꾸고, 그 밖의 말은 방문 목적(⑩비고)으로 남는다.
 * 고친 것은 화면에서 누른 것과 똑같이 남는다 (분류 근거 "사용자 수정", 관리자 모드면 "관리자 수정").
 * 못 알아들으면 아무것도 바꾸지 않는다 (앞에 남긴 말도 그대로 둔다).
 */
class TripDictation(
    private val voiceInput: () -> VoiceInput,
    private val trips: TripRepository,
    private val people: PeopleRepository,
    private val settings: SettingsRepository,
) {
    enum class Result { DONE, NOT_HEARD, NOT_ALLOWED, PERSON_LOCKED, PERSON_AMBIGUOUS }

    /** [admin] 은 버튼을 누른 순간의 관리자 상태 */
    suspend fun dictate(tripId: Long, admin: AdminState): Result {
        val spoken = voiceInput().listen()?.trim()
        if (spoken.isNullOrEmpty()) return Result.NOT_HEARD
        val trip = trips.find(tripId) ?: return Result.DONE // 듣는 사이 운행이 사라졌다
        return when (val edit = SpokenEdit.parse(spoken)) {
            is SpokenEdit.Classify -> classify(trip, edit.category, admin)
            is SpokenEdit.AssignPerson -> assignPerson(trip, edit, admin)
            is SpokenEdit.Note -> saveNote(trip, edit.text)
        }
    }

    private suspend fun classify(trip: Trip, category: Category, admin: AdminState): Result {
        // 기록 안 된 주행은 시각·장소를 몰라 출퇴근을 고를 수 없다 (화면 버튼과 같다)
        if (category !in Category.choicesFor(trip)) return Result.NOT_ALLOWED
        trips.classify(trip.id, category, admin.isAdminEdit)
        return Result.DONE
    }

    /** 혼자 쓰는 차이거나 등록된 사람이 없으면 사람 이야기가 아니라 방문 목적으로 남긴다 */
    private suspend fun assignPerson(trip: Trip, edit: SpokenEdit.AssignPerson, admin: AdminState): Result {
        if (!settings.settings.first().usage.isShared) return saveNote(trip, edit.spoken)
        val registered = people.people.first()
        val matches = edit.names.asSequence()
            .map { PersonMatcher.match(it, registered) }
            .firstOrNull { it.isNotEmpty() }
            ?: return saveNote(trip, edit.spoken)
        val person = matches.singleOrNull() ?: return Result.PERSON_AMBIGUOUS
        if (trip.personId == person.id) return Result.DONE
        // 운행 상세의 얼굴 고르기와 같은 규칙: 미지정이면 누구나, 이미 정해졌으면 관리 권한이 있을 때만
        if (trip.isGap || (!trip.isUnassigned && !admin.canManage)) return Result.PERSON_LOCKED
        trips.assignPerson(trip.id, person.id, admin.isAdminEdit)
        return Result.DONE
    }

    private suspend fun saveNote(trip: Trip, note: String): Result {
        trips.setNote(trip.id, note)
        return Result.DONE
    }
}
