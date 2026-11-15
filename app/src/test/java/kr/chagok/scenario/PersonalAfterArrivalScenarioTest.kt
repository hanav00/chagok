package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.Trip
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 개인 용무(이마트)를 안 바꾸고 다녀오면 업무로 기록된다.
 * 방금 운행 카드의 "개인 운행이었어요" 1탭으로 개인이 되고 위치가 지워진다.
 * 잘못 눌렀으면 "이건 업무였어요"로 원래대로. 삭제는 카드를 닫거나 다음에 출발할 때 확정된다.
 */
@RunWith(RobolectricTestRunner::class)
class PersonalAfterArrivalScenarioTest {
    private lateinit var app: TestApp
    private lateinit var martTrip: Trip

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun()
        app.navi.location = TripFixtures.OFFICE
        martTrip = app.driveTrip(km = 4.2, destination = TripFixtures.MART)
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `이마트에 다녀오면 기본값대로 업무로 기록되고 음성으로 업무라고 알려 준다`() {
        assertEquals(Category.BUSINESS, martTrip.category)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, martTrip.reason)
        assertEquals(TripFixtures.MART, martTrip.destination)
        assertEquals(listOf("이마트 도착, 업무로 기록했어요."), app.voiceOutput.spoken)
    }

    @Test
    fun `개인 운행이었어요 1탭 → 개인이 되고 출발지·도착지가 지워지며 근거는 사용자 수정`() = runBlocking<Unit> {
        app.trips.classify(martTrip.id, Category.PERSONAL)

        val changed = app.trips.find(martTrip.id)!!
        assertEquals(Category.PERSONAL, changed.category)
        assertEquals(ClassifyReason.USER_EDIT, changed.reason)
        assertNull(changed.origin)
        assertNull(changed.destination)
        assertEquals(4.2, changed.km, 1e-9)
        await(app.trips.restorableTripId) { it == martTrip.id }
    }

    @Test
    fun `잘못 눌렀으면 이건 업무였어요 → 원래 분류·장소·근거가 그대로 돌아온다`() = runBlocking<Unit> {
        app.trips.classify(martTrip.id, Category.PERSONAL)
        await(app.trips.restorableTripId) { it == martTrip.id }

        app.trips.revertToWork(martTrip.id)

        assertEquals(martTrip, app.trips.find(martTrip.id))
        await(app.trips.restorableTripId) { it == null }
    }

    @Test
    fun `카드를 닫아 삭제를 확정한 뒤에는 업무로 되돌려도 장소가 돌아오지 않는다`() = runBlocking<Unit> {
        app.trips.classify(martTrip.id, Category.PERSONAL)
        app.trips.commitPlaceDeletion()

        app.trips.revertToWork(martTrip.id)

        val reverted = app.trips.find(martTrip.id)!!
        assertEquals(Category.BUSINESS, reverted.category)
        assertEquals(ClassifyReason.USER_EDIT, reverted.reason)
        assertNull(reverted.origin)
        assertNull(reverted.destination)
    }

    @Test
    fun `개인으로 바꾼 뒤 다음에 출발하면 삭제가 확정되어 되살릴 수 없다`() = runBlocking<Unit> {
        app.trips.classify(martTrip.id, Category.PERSONAL)
        await(app.trips.restorableTripId) { it == martTrip.id }

        app.driveTrip(km = 3.0, destination = TripFixtures.CLIENT)
        await(app.trips.restorableTripId) { it == null }
        app.trips.revertToWork(martTrip.id)

        val reverted = app.trips.find(martTrip.id)!!
        assertEquals(Category.BUSINESS, reverted.category)
        assertNull(reverted.destination)
    }

    @Test
    fun `처음부터 개인으로 기록된 운행에 이건 업무였어요를 누르면 일반 업무, 근거는 사용자 수정`() = runBlocking<Unit> {
        app.nextDeparture.pickCategory(Category.PERSONAL)
        await(app.nextDeparture.next) { it?.category == Category.PERSONAL }
        val personal = app.driveTrip(km = 2.0, destination = TripFixtures.HOME)

        app.trips.revertToWork(personal.id)

        val reverted = app.trips.find(personal.id)!!
        assertEquals(Category.BUSINESS, reverted.category)
        assertEquals(ClassifyReason.USER_EDIT, reverted.reason)
        assertNull(reverted.destination)
    }
}
