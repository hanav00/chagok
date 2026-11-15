package kr.chagok

import android.content.res.Resources
import android.util.Log
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kr.chagok.car.NaviSource
import kr.chagok.car.VoiceInput
import kr.chagok.common.runSuspendCatching
import kr.chagok.data.NextDepartureStore
import kr.chagok.data.PeopleRepository
import kr.chagok.data.RefuelRepository
import kr.chagok.data.SettingsRepository
import kr.chagok.data.TripRepository
import kr.chagok.data.admin.AdminRepository
import kr.chagok.gleo.GleoCommands
import kr.chagok.recorder.RefuelDictation
import kr.chagok.recorder.RefuelWatcher
import kr.chagok.recorder.TripDictation
import kr.chagok.recorder.TripRecorder
import kr.chagok.recorder.TripVoice
import kr.chagok.ui.UserMessages
import java.time.Clock
import java.time.LocalDate

/**
 * 앱 하나에 하나씩만 있는 것들을 만들고 이어 준다 (수동 의존성 주입).
 * 기록 서비스와 화면이 같은 기록기·저장소를 본다. 화면은 ViewModel 을 거쳐서만 쓴다.
 * 바깥과 닿는 것은 [AppEnvironment] 로 받아서, 테스트도 이 클래스를 그대로 쓴다.
 */
class AppContainer(resources: Resources, environment: AppEnvironment) {
    private val scope = environment.scope
    private val vehicle = environment.vehicle

    val clock: Clock = environment.clock

    val today = Today(clock, scope)

    val settings = SettingsRepository(environment.settingsStore)
    val trips = TripRepository(environment.database, clock, scope)
    val people = PeopleRepository(environment.database, scope)
    val refuels = RefuelRepository(environment.database, scope)
    val admin = AdminRepository(environment.adminStore, scope)
    val nextDeparture = NextDepartureStore(settings.settings, people.people, scope)

    val navi: NaviSource = environment.navi
    val voiceInput: VoiceInput by lazy(environment.voiceInput)

    /** 음성 안내 한 문장. 기록기와 첫 실행 화면의 "들어 보기"가 같이 쓴다 */
    val tripVoice = TripVoice(resources, environment.voiceOutput)

    val recorder = TripRecorder(
        vehicle = vehicle,
        navi = navi,
        trips = trips,
        departure = nextDeparture,
        settings = settings.settings,
        admin = admin,
        tripVoice = tripVoice,
        clock = clock,
        scope = scope,
    )

    /** 마이크 받아쓰기 (방문 목적·분류·사람). 방금 운행 카드와 운행 상세 시트가 같이 쓴다 */
    val tripDictation = TripDictation({ voiceInput }, trips, people, settings)

    /** 연료·배터리가 오르면 주유·충전으로 남긴다. 금액은 홈에서 [refuelDictation] 으로 받는다 */
    val refuelWatcher = RefuelWatcher(vehicle, refuels, settings.settings, tripVoice, clock, scope)
    val refuelDictation = RefuelDictation({ voiceInput }, refuels)

    /** Gleo AI 가 깔린 차인지. 말하기 예시를 보일지 정한다 */
    val isGleoAvailable: Boolean = environment.isGleoAvailable

    /** Gleo 음성 명령 (분류·방문 목적·사람). Gleo 가 없는 차에서는 불리지 않을 뿐이다 */
    val gleo = GleoCommands(recorder, trips, people, nextDeparture, settings, clock)

    /** 화면 아래 잠깐 띄우는 알림 (쓰기 실패 등) */
    val messages = UserMessages()

    /** 기록 서비스가 뜰 때 한 번. 여러 번 불러도 한 번만 시작한다 */
    fun startRecording() {
        recorder.start()
        refuelWatcher.start()
        // 둘은 따로: 설치일 저장이 실패해도 차대번호는 읽는다. 차대번호는 차 연결을 기다려 늦게 온다
        scope.launch {
            runSuspendCatching { settings.markInstalled(LocalDate.now(clock)) }
                .onFailure { Log.e(TAG, "설치일 저장 실패", it) }
        }
        scope.launch {
            runSuspendCatching { vehicle.readVin()?.let { settings.setVin(it) } }
                .onFailure { Log.e(TAG, "차대번호 저장 실패", it) }
        }
    }

    /**
     * 시동(부팅) 직후 한 번, 이번 운행이 어떻게 기록될지 말해 개인 용무면 출발 전에 바꿀 수 있게 한다.
     * 음성 안내를 껐으면 말하지 않는다.
     * TODO: 실제 차의 시동 OFF 가 재부팅이 아니라 대기라면 부팅 신호가 안 와서 말하지 않는다. 실차 확인 뒤 다른 신호를 찾는다
     */
    fun announceIgnition() {
        scope.launch {
            runSuspendCatching {
                if (!settings.settings.first().isTripVoiceOn) return@runSuspendCatching
                val departure = recorder.currentDeparture.filterNotNull().first()
                tripVoice.announceIgnition(departure.category)
            }.onFailure { Log.e(TAG, "시동 안내 실패", it) }
        }
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}
