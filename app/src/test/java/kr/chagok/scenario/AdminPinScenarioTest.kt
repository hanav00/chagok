package kr.chagok.scenario

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.model.AdminEdit
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.DEMO_PIN
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.setPinWithNewKey
import kr.chagok.testing.setUpLockedAdmin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.LocalDate

/**
 * 관리자 PIN (공용차). PIN 을 정하고 잠그면 운전자는 오늘 운행만 연다.
 * PIN 으로 열고 지난 운행을 고치면 근거가 "관리자 수정"이 되고 수정 기록에 남는다. 출발하면 저절로 잠긴다.
 */
@RunWith(RobolectricTestRunner::class)
class AdminPinScenarioTest {
    private lateinit var app: TestApp
    private lateinit var yesterdayTrip: Trip
    private lateinit var todayTrip: Trip
    private lateinit var recoveryKey: String

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김지은"))
        app.nextDeparture.pickPerson(app.people.people.value.first().id)
        await(app.nextDeparture.next) { it?.personId != null }
        yesterdayTrip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.clock.advance(Duration.ofDays(1))
        todayTrip = app.driveTrip(km = 5.0, destination = TripFixtures.OFFICE)

        recoveryKey = app.setUpLockedAdmin()
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `PIN 을 정하면 16자리 숫자 복구키를 한 번 받는다`() {
        assertEquals(ChagokConfig.RECOVERY_KEY_LENGTH, recoveryKey.length)
        assertTrue(recoveryKey.all(Char::isDigit))
    }

    @Test
    fun `잠긴 동안 운전자는 오늘 운행만 열 수 있고 지난 기록·내보내기는 못 한다`() {
        val state = app.admin.state.value
        val today = LocalDate.now(app.clock)

        assertFalse(state.canManage)
        assertTrue(state.canOpen(todayTrip, today))
        assertFalse(state.canOpen(yesterdayTrip, today))
    }

    @Test
    fun `PIN 을 넣으면 관리자 모드가 되어 지난 운행도 열린다`() = runBlocking<Unit> {
        assertTrue(app.admin.unlock(DEMO_PIN))

        val state = await(app.admin.state) { it.isUnlocked }
        assertTrue(state.canManage)
        assertTrue(state.isAdminEdit)
        assertTrue(state.canOpen(yesterdayTrip, LocalDate.now(app.clock)))
    }

    @Test
    fun `관리자가 지난 운행을 개인으로 고치면 근거가 관리자 수정이 되고 무엇을 바꿨는지 수정 기록에 남는다`() = runBlocking<Unit> {
        app.admin.unlock(DEMO_PIN)
        val state = await(app.admin.state) { it.isUnlocked }

        app.trips.classify(yesterdayTrip.id, Category.PERSONAL, isAdminEdit = state.isAdminEdit)

        val edited = app.trips.find(yesterdayTrip.id)!!
        assertEquals(Category.PERSONAL, edited.category)
        assertEquals(ClassifyReason.ADMIN_EDIT, edited.reason)
        assertNull(edited.destination)
        val log = await(app.trips.adminEdits) { it.isNotEmpty() }.single()
        assertEquals(yesterdayTrip.id, log.tripId)
        assertEquals(yesterdayTrip.date, log.tripDate)
        assertEquals(AdminEdit.Field.CATEGORY, log.field)
        assertEquals("BUSINESS", log.fromValue)
        assertEquals("PERSONAL", log.toValue)
    }

    @Test
    fun `관리자가 이미 정해진 사람을 바꾸면 수정 기록에 남고 분류 근거는 그대로다`() = runBlocking<Unit> {
        app.admin.unlock(DEMO_PIN)
        val other = app.people.people.value[1]

        app.trips.assignPerson(yesterdayTrip.id, other.id, isAdminEdit = true)

        val edited = app.trips.find(yesterdayTrip.id)!!
        assertEquals(other.id, edited.personId)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, edited.reason)
        val log = await(app.trips.adminEdits) { it.isNotEmpty() }.single()
        assertEquals(AdminEdit.Field.PERSON, log.field)
        assertEquals(yesterdayTrip.personId.toString(), log.fromValue)
        assertEquals(other.id.toString(), log.toValue)
    }

    @Test
    fun `운전자가 오늘 운행을 고친 것은 사용자 수정이고 수정 기록에 남지 않는다`() = runBlocking<Unit> {
        val state = app.admin.state.value

        app.trips.classify(todayTrip.id, Category.PERSONAL, isAdminEdit = state.isAdminEdit)

        assertEquals(ClassifyReason.USER_EDIT, app.trips.find(todayTrip.id)!!.reason)
        assertTrue(app.trips.adminEdits.first().isEmpty())
    }

    @Test
    fun `관리자 모드로 둔 채 출발하면 저절로 잠긴다`() = runBlocking<Unit> {
        app.admin.unlock(DEMO_PIN)
        await(app.admin.state) { it.isUnlocked }

        app.startDriving()

        val state = await(app.admin.state) { !it.isUnlocked }
        assertFalse(state.canManage)
    }

    @Test
    fun `PIN 을 틀리면 열리지 않고 남은 횟수가 준다, 5번 틀리면 맞는 PIN 으로도 못 열고 복구키로만 연다`() = runBlocking<Unit> {
        assertFalse(app.admin.unlock("0000"))
        assertEquals(
            ChagokConfig.PIN_MAX_TRIES - 1,
            await(app.admin.state) { it.triesLeft < ChagokConfig.PIN_MAX_TRIES }.triesLeft,
        )

        repeat(ChagokConfig.PIN_MAX_TRIES - 1) { app.admin.unlock("0000") }

        assertTrue(await(app.admin.state) { it.isPinBlocked }.isPinBlocked)
        assertFalse(app.admin.unlock(DEMO_PIN))
        assertTrue(app.admin.checkRecoveryKey(recoveryKey))
        assertFalse(app.admin.checkRecoveryKey("0".repeat(ChagokConfig.RECOVERY_KEY_LENGTH)))

        app.admin.changePin("1357")
        val state = await(app.admin.state) { it.isUnlocked }
        assertEquals(ChagokConfig.PIN_MAX_TRIES, state.triesLeft)
        assertTrue(app.admin.checkRecoveryKey(recoveryKey))
    }

    @Test
    fun `관리자를 넘기면 새 PIN·새 복구키가 되고 예전 PIN·복구키는 더 못 쓴다`() = runBlocking<Unit> {
        app.admin.unlock(DEMO_PIN)

        val newKey = app.admin.setPinWithNewKey("1357")
        app.admin.lock()

        assertFalse(app.admin.checkRecoveryKey(recoveryKey))
        assertTrue(app.admin.checkRecoveryKey(newKey))
        assertFalse(app.admin.unlock(DEMO_PIN))
        assertTrue(app.admin.unlock("1357"))
    }
}
