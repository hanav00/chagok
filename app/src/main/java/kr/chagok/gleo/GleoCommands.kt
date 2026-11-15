package kr.chagok.gleo

import android.util.Log
import kotlinx.coroutines.flow.first
import kr.chagok.common.runSuspendCatching
import kr.chagok.data.NextDepartureStore
import kr.chagok.data.PeopleRepository
import kr.chagok.data.SettingsRepository
import kr.chagok.data.TripRepository
import kr.chagok.domain.PersonMatcher
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.Trip
import kr.chagok.recorder.TripRecorder
import org.json.JSONObject
import java.time.Clock
import java.time.LocalDate

/**
 * Gleo 음성 명령("방금 운행은 개인이었어" 등). 음성은 운전자 권한만 쓴다: 오늘 운행만 고치고 이미 정해진 사람은
 * 바꾸지 않는다. 고친 것은 화면에서 고친 것과 똑같이 남는다 (분류 근거 "사용자 수정", 개인이면 위치 삭제).
 */
class GleoCommands(
    private val recorder: TripRecorder,
    private val trips: TripRepository,
    private val people: PeopleRepository,
    private val departure: NextDepartureStore,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) {
    /** 저장이 실패해도 던지지 않고 실패로 회신한다. Gleo 가 사용자에게 알린다 */
    suspend fun handle(function: String, params: JSONObject): GleoReply {
        val reply = runSuspendCatching {
            when (function) {
                GleoProtocol.SET_TRIP_CATEGORY -> setCategory(params)
                GleoProtocol.ADD_TRIP_NOTE -> addNote(params.optString(GleoProtocol.PARAM_NOTE))
                GleoProtocol.SET_TRIP_USER -> setUser(params.optString(GleoProtocol.PARAM_NAME))
                else -> GleoReply.fail(Messages.UNKNOWN_FUNCTION)
            }
        }.getOrElse {
            Log.e(TAG, "$function 처리 실패", it)
            GleoReply.fail(Messages.SAVE_FAILED)
        }
        // 홈 시작 가이드 "Gleo로 말해 보기". 남기기가 실패해도 명령 결과는 그대로 돌려준다
        if (reply.status == GleoReply.Status.SUCCESS) {
            runSuspendCatching { settingsRepository.markChangeTried() }.onFailure { Log.e(TAG, "말해 보기 남기기 실패", it) }
        }
        return reply
    }

    private suspend fun setCategory(params: JSONObject): GleoReply {
        val category = parseCategory(params.optString(GleoProtocol.PARAM_CATEGORY))
            ?: return GleoReply.asking(Messages.ASK_CATEGORY)
        return setCategory(category, GleoTripTarget.parse(params.optString(GleoProtocol.PARAM_TRIP)))
    }

    /**
     * Gleo 가 운행을 정해 주지 않으면 주행 중엔 이번 운행을 바꾼다. 주차 중이면 시동 뒤 아직 안 달렸을 때는
     * 곧 떠날 운행이 분명하고, 달린 적이 있으면 방금 운행인지 다음 운행인지 되묻는다.
     */
    suspend fun setCategory(category: Category, target: GleoTripTarget?): GleoReply {
        if (target == GleoTripTarget.NEXT) return setNextCategory(category)
        if (target != GleoTripTarget.LAST && recorder.reclassifyDriving(category) != null) {
            return GleoReply.success(Messages.currentTripIs(category), categoryData(category))
        }
        if (target == GleoTripTarget.LAST) return setLastCategory(category)
        if (!recorder.hasParkedSinceStart) return setNextCategory(category)
        return GleoReply.asking(Messages.ASK_WHICH_TRIP)
    }

    private suspend fun setLastCategory(category: Category): GleoReply {
        val trip = lastTripToday() ?: return GleoReply.fail(Messages.NO_TRIP_TODAY)
        when {
            trip.category == category -> Unit
            // "이건 업무였어요" 버튼처럼 지웠던 장소까지 되살린다. 되살린 분류가 말한 것과 다르면 맞춘다
            trip.isPersonal -> {
                trips.revertToWork(trip.id)
                if (trips.find(trip.id)?.category != category) trips.classify(trip.id, category)
            }
            else -> trips.classify(trip.id, category)
        }
        return GleoReply.success(Messages.lastTripIs(category), categoryData(category))
    }

    // 다음 출발 값은 주차하면 비워진다. 주행 중에 정하면 곧 사라지니 주차한 뒤에 정하게 한다
    private fun setNextCategory(category: Category): GleoReply {
        if (recorder.isDriving.value) return GleoReply.fail(Messages.NEXT_AFTER_PARKING)
        departure.pickCategory(category)
        return GleoReply.success(Messages.nextTripIs(category), categoryData(category))
    }

    /** 방문 목적을 오늘 마지막 운행의 ⑩비고에 남긴다. 업무 운행만 (화면의 받아쓰기 버튼과 같다) */
    suspend fun addNote(spoken: String): GleoReply {
        val note = spoken.trim()
        if (note.isEmpty()) return GleoReply.asking(Messages.ASK_NOTE)
        // TODO: 주행 중에 말한 목적을 진행 중 운행에 들고 있다가 주차할 때 붙일지 정한다 (지금은 주차한 뒤에만)
        if (recorder.isDriving.value) return GleoReply.fail(Messages.NOTE_AFTER_PARKING)
        val trip = lastTripToday() ?: return GleoReply.fail(Messages.NO_TRIP_TODAY)
        if (trip.isPersonal) return GleoReply.fail(Messages.NO_NOTE_ON_PERSONAL)
        trips.setNote(trip.id, note)
        return GleoReply.success(Messages.NOTE_SAVED)
    }

    /**
     * 차를 쓰는 사람 정하기 (공용차). 주행 중이면 이번 운행, 주차 중이면 오늘 마지막 운행이 미지정일 때 그 운행,
     * 아니면 다음 출발의 "누가 타나요?"
     */
    suspend fun setUser(spokenName: String): GleoReply {
        if (!settingsRepository.settings.first().usage.isShared) return GleoReply.fail(Messages.SOLO_CAR)
        val matches = PersonMatcher.match(spokenName, people.people.first())
        val person = when (matches.size) {
            0 -> return GleoReply.fail(Messages.NO_SUCH_PERSON)
            1 -> matches.single()
            else -> return GleoReply.asking(Messages.ASK_FULL_NAME)
        }
        val data = mapOf(GleoProtocol.PARAM_NAME to person.name)
        recorder.assignDrivingPerson(person.id)?.let { driving ->
            return if (driving.departure.personId == person.id) {
                GleoReply.success(Messages.CURRENT_USER_SET, data)
            } else {
                GleoReply.fail(Messages.USER_ALREADY_SET)
            }
        }
        val last = lastTripToday()
        if (last != null && last.isUnassigned) {
            trips.assignPerson(last.id, person.id)
            return GleoReply.success(Messages.LAST_USER_SET, data)
        }
        departure.pickPerson(person.id)
        return GleoReply.success(Messages.NEXT_USER_SET, data)
    }

    private suspend fun lastTripToday(): Trip? = trips.lastTripOn(LocalDate.now(clock))

    private fun categoryData(category: Category) = mapOf(GleoProtocol.PARAM_CATEGORY to category.name)

    private object Messages {
        const val UNKNOWN_FUNCTION = "This function is not supported."
        const val SAVE_FAILED = "The logbook could not be saved. Please try again on the screen."
        const val ASK_CATEGORY = "Should the trip be recorded as business, commute, or personal?"
        const val ASK_WHICH_TRIP = "Should I change the trip that just ended, or the next trip?"
        const val NO_TRIP_TODAY = "There is no trip recorded today to change."
        const val NEXT_AFTER_PARKING = "The next trip can be set after parking."
        const val ASK_NOTE = "What was the purpose of the visit?"
        const val NOTE_AFTER_PARKING = "The visit purpose can be saved after parking."
        const val NO_NOTE_ON_PERSONAL = "Personal trips do not keep a visit purpose."
        const val NOTE_SAVED = "The visit purpose is saved on the last trip."
        const val SOLO_CAR = "This car is set up for one person, so the user is always the same."
        const val NO_SUCH_PERSON = "There is no registered person with that name."
        const val ASK_FULL_NAME = "Several people match that name. Please say the full name."
        const val CURRENT_USER_SET = "The current trip is recorded under that person."
        const val USER_ALREADY_SET = "This trip already has a user. Only the administrator can change it."
        const val LAST_USER_SET = "The last trip is recorded under that person."
        const val NEXT_USER_SET = "The next trip will be recorded under that person."

        fun currentTripIs(category: Category) = "The current trip is now recorded as ${usage(category)}."
        fun lastTripIs(category: Category) = "The last trip is now recorded as ${usage(category)}."
        fun nextTripIs(category: Category) = "The next trip will be recorded as ${usage(category)}."
        private fun usage(category: Category) = when (category) {
            Category.PERSONAL -> "personal use"
            Category.COMMUTE -> "commute between home and office"
            Category.BUSINESS -> "business use"
        }
    }

    companion object {
        private const val TAG = "GleoCommands"

        /** 업무·출퇴근·개인. 출퇴근도 말로 정할 수 있다. 모르는 값이면 null */
        fun parseCategory(value: String?): Category? =
            Category.PICK_ORDER.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}
