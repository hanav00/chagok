package kr.chagok.ui.home

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.ScriptedVoiceInput
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.ViewModelTestRule
import kr.chagok.testing.installedOnDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 홈 "방금 운행" 카드.
 * 카드 제목은 도착지, 부제는 "시각 · 거리 · 분류 · 처음 간 곳 · (사람이 정했으면) 근거".
 * 여기서는 그 글자를 만드는 값(운행·처음 간 곳·근거·되돌릴 수 있는지)을 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
class HomeRecentCardTest {
    @get:Rule
    val rule = ViewModelTestRule()
    private val app get() = rule.app

    private suspend fun home(
        usage: VehicleUsage = VehicleUsage.SOLO,
        people: List<String> = listOf("박정훈"),
    ): HomeViewModel {
        app.startRecording()
        app.completeFirstRun(usage = usage, people = people)
        return rule.viewModel { HomeViewModel(app.container, rule.timerContext) }
    }

    private suspend fun HomeViewModel.awaitRecent(condition: (RecentCardState) -> Boolean = { true }): RecentCardState =
        await(state) { s -> s?.recent?.let(condition) == true }!!.recent!!

    @Test
    fun `처음 가는 고객사에 다녀옴 → 업무 카드, 도착지, 계기판 거리, 처음 간 곳`() = runBlocking<Unit> {
        val home = home()

        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        val card = home.awaitRecent()
        assertEquals(trip.id, card.trip.id)
        assertTrue(card.isWork)
        assertEquals(TripFixtures.CLIENT.name, card.trip.destination?.name) // 블록 글자 "○○전자 · 8.8 km"
        assertEquals(8.8, card.trip.km, 1e-9)
        assertEquals(Category.BUSINESS, card.trip.category)
        assertTrue("처음 간 곳", card.isFirstVisit)
        assertFalse("기본값 그대로라 근거를 붙이지 않는다", card.trip.reason!!.isChangedByUser)
        assertFalse("혼자 쓰는 차는 누가 탔는지 묻지 않는다", card.hasFacePicker)
    }

    @Test
    fun `같은 고객사에 두 번째로 감 → 처음 간 곳 표시가 없다`() = runBlocking<Unit> {
        val home = home()
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.navi.destination.value = null

        val second = app.driveTrip(km = 9.0, destination = TripFixtures.CLIENT)

        val card = home.awaitRecent { it.trip.id == second.id }
        assertFalse(card.isFirstVisit)
    }

    @Test
    fun `오늘 두 번째 운행 → 카드 블록 탑 아래 칸에 첫 운행, 한 번 쌓이면 다시 떨어지지 않는다`() = runBlocking<Unit> {
        val home = home()
        val first = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        app.navi.destination.value = null

        val second = app.driveTrip(km = 9.0)

        val card = home.awaitRecent { it.trip.id == second.id }
        assertEquals(listOf(first.id), card.earlierToday.map { it.id })
        assertFalse("처음 뜬 카드는 블록이 떨어진다", card.isLanded)

        rule.onMain { home.markRecentLanded(second.id) }
        home.awaitRecent { it.isLanded }
    }

    @Test
    fun `앞 운행 블록이 쌓인 뒤 새 운행이 들어옴 → 새 운행 블록은 다시 떨어진다`() = runBlocking<Unit> {
        val home = home()
        val first = app.driveTrip(km = 8.8)
        home.awaitRecent { it.trip.id == first.id }
        rule.onMain { home.markRecentLanded(first.id) }

        val second = app.driveTrip(km = 9.0)

        assertFalse(home.awaitRecent { it.trip.id == second.id }.isLanded)
    }

    @Test
    fun `출발 전에 개인을 고르고 다녀옴 → 개인 카드, 장소 없음, 근거 출발 전 선택`() = runBlocking<Unit> {
        val home = home()
        rule.onMain { home.pickNextCategory(Category.PERSONAL) }
        await(home.state) { it?.next?.category == Category.PERSONAL } // 토글이 바뀐 것을 보고 출발한다

        app.driveTrip(km = 5.0, destination = TripFixtures.HOME)

        val card = home.awaitRecent()
        assertFalse(card.isWork)
        assertEquals(Category.PERSONAL, card.trip.category)
        assertNull("개인 운행은 장소를 남기지 않는다", card.trip.destination)
        assertEquals(ClassifyReason.PRE_PICK, card.trip.reason)
        assertTrue("사람이 정한 분류라 부제에 근거가 붙는다", card.trip.reason!!.isChangedByUser)
        assertFalse("처음부터 개인이라 되살릴 장소가 없다", card.canRestorePlaces)
    }

    @Test
    fun `개인 운행이었어요 → 개인으로 바뀌고 장소를 지우지만 아직 되돌릴 수 있다`() = runBlocking<Unit> {
        val home = home()
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        home.awaitRecent()

        rule.onMain { home.recentToPersonal() }

        val card = home.awaitRecent { !it.isWork }
        assertEquals(trip.id, card.trip.id)
        assertNull(card.trip.destination)
        assertNull(card.trip.origin)
        assertEquals(ClassifyReason.USER_EDIT, card.trip.reason)
        assertTrue("부제: 카드를 닫으면 장소를 지워요", card.canRestorePlaces)
    }

    @Test
    fun `개인으로 바꾼 뒤 이건 업무였어요 → 원래 분류·장소·근거로 돌아온다`() = runBlocking<Unit> {
        val home = home()
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        home.awaitRecent()
        rule.onMain { home.recentToPersonal() }
        home.awaitRecent { !it.isWork }

        rule.onMain { home.recentToWork() }

        val card = home.awaitRecent { it.isWork }
        assertEquals(Category.BUSINESS, card.trip.category)
        assertEquals(TripFixtures.CLIENT, card.trip.destination)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, card.trip.reason)
        assertFalse(card.canRestorePlaces)
    }

    @Test
    fun `처음부터 개인이던 운행에 이건 업무였어요 → 일반 업무, 근거 사용자 수정`() = runBlocking<Unit> {
        val home = home()
        rule.onMain { home.pickNextCategory(Category.PERSONAL) }
        await(home.state) { it?.next?.category == Category.PERSONAL } // 토글이 바뀐 것을 보고 출발한다
        app.driveTrip(km = 5.0)
        home.awaitRecent { !it.isWork }

        rule.onMain { home.recentToWork() }

        val card = home.awaitRecent { it.isWork }
        assertEquals(Category.BUSINESS, card.trip.category)
        assertEquals(ClassifyReason.USER_EDIT, card.trip.reason)
    }

    @Test
    fun `카드를 닫음 → 카드 대신 닫았어요 줄, 6초가 지나면 줄도 사라지고 다시 볼 수 없다`() = runBlocking<Unit> {
        val home = home()
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        home.awaitRecent()

        rule.onMain { home.dismissRecent() }

        val closed = await(home.state) { it?.isRecentClosed == true }!!
        assertNull(closed.recent)
        assertNull("기록기도 방금 운행을 잊는다 (다음에 앱을 열어도 카드가 다시 뜨지 않게)", app.recorder.recentTripId.value)

        rule.advanceTimeBy(ChagokConfig.RECENT_CLOSED_MILLIS)

        val after = await(home.state) { it?.isRecentClosed == false }!!
        assertNull(after.recent)
        rule.onMain { home.reopenRecent() }
        assertNull("줄이 사라진 뒤에는 다시 보기가 없다", app.recorder.recentTripId.value)
    }

    @Test
    fun `닫은 지 6초가 되기 직전 → 아직 다시 보기로 열 수 있다`() = runBlocking<Unit> {
        val home = home()
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        home.awaitRecent()
        rule.onMain { home.dismissRecent() }

        rule.advanceTimeBy(ChagokConfig.RECENT_CLOSED_MILLIS - 1)
        rule.onMain { home.reopenRecent() }

        assertEquals(trip.id, app.recorder.recentTripId.value)
        assertEquals(trip.id, home.awaitRecent().trip.id)
    }

    @Test
    fun `닫았어요 줄의 다시 보기 → 같은 운행 카드가 다시 뜨고 닫음 줄은 사라진다`() = runBlocking<Unit> {
        val home = home()
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        home.awaitRecent()
        rule.onMain { home.dismissRecent() }
        await(home.state) { it?.isRecentClosed == true }

        rule.onMain { home.reopenRecent() }

        val state = await(home.state) { it?.recent != null }!!
        assertEquals(trip.id, state.recent!!.trip.id)
        assertFalse(state.isRecentClosed)
        // 다시 연 카드는 타이머가 끝나도 그대로다
        rule.advanceTimeBy(ChagokConfig.RECENT_CLOSED_MILLIS)
        assertEquals(trip.id, app.recorder.recentTripId.value)
    }

    @Test
    fun `개인으로 바꾸고 카드를 닫음 → 장소 삭제 확정, 다시 열어 업무로 바꿔도 장소는 돌아오지 않는다`() = runBlocking<Unit> {
        val home = home()
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        home.awaitRecent()
        rule.onMain { home.recentToPersonal() }
        home.awaitRecent { it.canRestorePlaces && !it.isWork }

        rule.onMain { home.dismissRecent() }
        await(home.state) { it?.isRecentClosed == true }
        rule.onMain { home.reopenRecent() }
        val reopened = home.awaitRecent()
        assertFalse(reopened.canRestorePlaces)

        rule.onMain { home.recentToWork() }
        val card = home.awaitRecent { it.isWork }
        assertNull(card.trip.destination)
        assertEquals(ClassifyReason.USER_EDIT, card.trip.reason)
    }

    @Test
    fun `새 운행이 들어옴 → 앞 카드의 닫음 줄은 잊고 새 카드를 보여 준다`() = runBlocking<Unit> {
        val home = home()
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        home.awaitRecent()
        rule.onMain { home.dismissRecent() }
        await(home.state) { it?.isRecentClosed == true }

        val next = app.driveTrip(km = 2.0)

        val state = await(home.state) { it?.recent?.trip?.id == next.id }!!
        assertFalse(state.isRecentClosed)
    }

    @Test
    fun `마이크로 방문 목적을 말함 → 비고에 남는다`() = runBlocking<Unit> {
        app.voiceInput = ScriptedVoiceInput("신제품 미팅")
        val home = home()
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        home.awaitRecent()

        rule.onMain { home.dictate() }

        val card = home.awaitRecent { it.trip.note.isNotEmpty() }
        assertEquals("신제품 미팅", card.trip.note)
        assertFalse(card.isListening)
    }

    @Test
    fun `마이크로 못 알아들음 → 비고는 비어 있고, 다시 누르면 새로 듣는다`() = runBlocking<Unit> {
        app.voiceInput = ScriptedVoiceInput(null, "거래처 납품")
        val home = home()
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        home.awaitRecent()

        rule.onMain { home.dictate() }
        val missed = home.awaitRecent { !it.isListening }
        assertEquals("", missed.trip.note)

        rule.onMain { home.dictate() }
        assertEquals("거래처 납품", home.awaitRecent { it.trip.note.isNotEmpty() }.trip.note)
    }

    @Test
    fun `공용차에서 사람을 안 고르고 다녀옴 → 카드에 누가 탔나요, 고르면 그 사람으로 남고 얼굴 줄은 그대로`() = runBlocking<Unit> {
        val home = home(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)
        val card = home.awaitRecent()
        assertTrue(card.hasFacePicker)
        assertNull(card.trip.personId)
        assertNull("카드에 있는 운행은 미지정 목록에 따로 넣지 않는다", home.state.value!!.unassigned)

        val kim = app.container.people.people.value[1]
        rule.onMain { home.pickRecentPerson(kim.id) }

        val picked = home.awaitRecent { it.trip.personId == kim.id }
        assertEquals(trip.id, picked.trip.id)
        assertTrue("고른 뒤에도 바꿀 수 있게 남긴다", picked.hasFacePicker)
    }

    @Test
    fun `공용차에서 사람을 고르고 출발 → 카드에 얼굴 줄이 없다`() = runBlocking<Unit> {
        val home = home(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        rule.onMain { home.pickNextPerson(app.container.people.people.value[0].id) }
        await(home.state) { it?.next?.who?.selectedId != null }

        app.driveTrip(km = 3.0)

        val card = home.awaitRecent()
        assertEquals(app.container.people.people.value[0].id, card.trip.personId)
        assertFalse(card.hasFacePicker)
    }

    @Test
    fun `설치 3일째 → 카드 아래 사용법 안내가 보인다`() = runBlocking<Unit> {
        app.installedOnDay(3)
        val home = home()

        app.driveTrip(km = 3.0)

        assertTrue(home.awaitRecent().hasHint)
    }

    @Test
    fun `설치 4일째 → 카드 아래 사용법 안내가 없다`() = runBlocking<Unit> {
        app.installedOnDay(4)
        val home = home()

        app.driveTrip(km = 3.0)

        assertFalse(home.awaitRecent().hasHint)
    }
}
