package kr.chagok.domain.model

import kr.chagok.testing.TripFixtures.TODAY
import kr.chagok.testing.TripFixtures.trip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 관리자 PIN 잠금 상태 [AdminState] */
class AdminStateTest {
    private val noPin = AdminState(hasPin = false)
    private val locked = AdminState(hasPin = true, isUnlocked = false, triesLeft = 5)
    private val unlocked = AdminState(hasPin = true, isUnlocked = true, triesLeft = 5)

    private val todayTrip = trip(date = TODAY)
    private val pastTrip = trip(date = TODAY.minusDays(1))

    @Test
    fun `PIN 을 정하지 않은 차는 누구나 관리한다 (지난 기록·내보내기·기록 방식)`() {
        assertTrue(noPin.canManage)
    }

    @Test
    fun `PIN 을 정하고 잠긴 차는 관리할 수 없다`() {
        assertFalse(locked.canManage)
    }

    @Test
    fun `PIN 으로 풀면 관리할 수 있다`() {
        assertTrue(unlocked.canManage)
    }

    @Test
    fun `관리자 수정으로 남는 건 PIN 을 정하고 풀었을 때만`() {
        assertTrue(unlocked.isAdminEdit)
        assertFalse(locked.isAdminEdit)
        assertFalse(noPin.isAdminEdit)
        assertFalse(AdminState(hasPin = false, isUnlocked = true).isAdminEdit) // PIN 이 없으면 풀림 표시가 남아 있어도 아님
    }

    @Test
    fun `남은 횟수가 0이면 PIN 으로 못 열고 복구키로만 연다`() {
        assertTrue(AdminState(hasPin = true, triesLeft = 0).isPinBlocked)
        assertFalse(AdminState(hasPin = true, triesLeft = 1).isPinBlocked)
    }

    @Test
    fun `PIN 이 없는 차는 남은 횟수와 상관없이 막히지 않는다`() {
        assertFalse(AdminState(hasPin = false, triesLeft = 0).isPinBlocked)
    }

    @Test
    fun `잠긴 차에서 운전자는 오늘 운행만 열 수 있다`() {
        assertTrue(locked.canOpen(todayTrip, TODAY))
        assertFalse(locked.canOpen(pastTrip, TODAY))
    }

    @Test
    fun `PIN 이 없거나 풀려 있으면 지난 운행도 열 수 있다`() {
        assertTrue(noPin.canOpen(pastTrip, TODAY))
        assertTrue(unlocked.canOpen(pastTrip, TODAY))
    }
}
