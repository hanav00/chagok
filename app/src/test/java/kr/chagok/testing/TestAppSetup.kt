package kr.chagok.testing

import kr.chagok.data.admin.AdminRepository
import kr.chagok.domain.model.Trip
import kr.chagok.recorder.TripDictation
import kr.chagok.testing.TestApp.Companion.await

// 화면 테스트가 자주 만드는 앱 상태. 테스트마다 다른 값만 넘긴다

const val DEMO_PIN = "2580"

/** 처음 정하기·관리자 넘기기를 한 번에: 새 복구키를 만들어 PIN 과 함께 저장하고 돌려준다 (화면은 키를 보여 준 뒤에 저장한다) */
suspend fun AdminRepository.setPinWithNewKey(pin: String): String =
    newRecoveryKey().also { setPinAndKey(pin, it) }

/**
 * 운행을 기록부에 바로 넣는다. id 는 넣은 순서대로 붙는다 (빈 기록부면 1, 2, 3 …).
 * 저장된 운행(id 포함)을 넣은 순서대로 돌려준다.
 */
suspend fun TestApp.seedTrips(vararg list: Trip): List<Trip> {
    val before = trips.trips.value.size
    trips.importTrips(list.toList())
    return await(trips.trips) { it.size == before + list.size }.takeLast(list.size)
}

/** 관리자 PIN 을 정하고 잠근 상태 (운전자가 탄 차). 복구키를 돌려준다 */
suspend fun TestApp.setUpLockedAdmin(pin: String = DEMO_PIN): String {
    val key = container.admin.setPinWithNewKey(pin)
    container.admin.lock()
    await(container.admin.state) { it.hasPin && !it.isUnlocked }
    return key
}

/** 관리자 PIN 을 정하고 풀어 둔 상태 (관리자가 보고 있음). 복구키를 돌려준다 */
suspend fun TestApp.setUpUnlockedAdmin(pin: String = DEMO_PIN): String {
    val key = container.admin.setPinWithNewKey(pin)
    await(container.admin.state) { it.hasPin && it.isUnlocked }
    return key
}

/** 앱을 설치한 지 [day] 일째인 상태 (1 = 설치한 날). [startRecording] 보다 먼저 불러야 한다 (설치일은 한 번만 남는다) */
suspend fun TestApp.installedOnDay(day: Int) {
    val installedOn = TripFixtures.TODAY.minusDays(day - 1L)
    container.settings.markInstalled(installedOn)
    await(container.settings.settings) { it.installedOn == installedOn }
}

/** 마이크를 누른 것처럼 받아쓴다. 관리자 상태는 지금 값 (화면도 누른 순간의 값을 쓴다) */
suspend fun TestApp.dictate(tripId: Long): TripDictation.Result =
    container.tripDictation.dictate(tripId, admin.state.value)
