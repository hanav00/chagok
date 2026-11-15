package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 기본 분류를 개인으로 바꾸면 섞어 쓰는 차처럼 업무일 때만 "업무"를 누르는 흐름이 된다.
 * 첫 실행에서 고르거나 설정 "기록 방식 → 기본 분류"에서 바꾼다.
 */
@RunWith(RobolectricTestRunner::class)
class DefaultPersonalScenarioTest {
    private lateinit var app: TestApp

    @Before
    fun setUp() {
        app = TestApp()
        app.startRecording()
        app.navi.location = TripFixtures.HOME
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `기본값 개인인 차는 아무것도 안 누르면 장소 없이 개인으로 기록되고 근거는 기본값 개인`() = runBlocking<Unit> {
        app.completeFirstRun(defaultCategory = Category.PERSONAL)

        val trip = app.driveTrip(km = 6.0, destination = TripFixtures.MART)

        assertEquals(Category.PERSONAL, trip.category)
        assertEquals(ClassifyReason.DEFAULT_PERSONAL, trip.reason)
        assertNull(trip.origin)
        assertNull(trip.destination)
        assertEquals(0, app.navi.locationRequests)
    }

    @Test
    fun `기본값 개인인 차에서 출발 전에 업무를 누르면 그 운행만 업무, 근거는 출발 전 선택`() = runBlocking<Unit> {
        app.completeFirstRun(defaultCategory = Category.PERSONAL)
        app.nextDeparture.pickCategory(Category.BUSINESS)
        val next = await(app.nextDeparture.next) { it?.category == Category.BUSINESS }!!
        assertTrue(next.isChanged)

        val work = app.driveTrip(km = 15.0, destination = TripFixtures.CLIENT)
        val afterwards = app.driveTrip(km = 15.0, destination = TripFixtures.HOME)

        assertEquals(Category.BUSINESS, work.category)
        assertEquals(ClassifyReason.PRE_PICK, work.reason)
        assertEquals(TripFixtures.HOME, work.origin)
        assertEquals(TripFixtures.CLIENT, work.destination)
        assertEquals(listOf("○○전자 도착, 업무로 기록했어요.", "15.0킬로미터 운행, 개인으로 기록했어요."), app.voiceOutput.spoken)
        assertEquals(Category.PERSONAL, afterwards.category)
        assertEquals(ClassifyReason.DEFAULT_PERSONAL, afterwards.reason)
    }

    @Test
    fun `업무로 시작한 차도 설정에서 기본 분류를 개인으로 바꾸면 다음 운행부터 개인`() = runBlocking<Unit> {
        app.completeFirstRun(defaultCategory = Category.BUSINESS)
        val before = app.driveTrip(km = 5.0, destination = TripFixtures.CLIENT)

        app.settings.setDefaultCategory(Category.PERSONAL)
        await(app.nextDeparture.next) { it?.category == Category.PERSONAL && !it.isChanged }
        val after = app.driveTrip(km = 5.0, destination = TripFixtures.MART)

        assertEquals(Category.BUSINESS, before.category)
        assertEquals(Category.PERSONAL, after.category)
        assertEquals(ClassifyReason.DEFAULT_PERSONAL, after.reason)
    }
}
