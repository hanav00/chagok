package kr.chagok.scenario

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.gleo.GleoProtocol
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.ui.guide.ChangePractice
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 첫 실행 "말해 보기"·홈 시작 가이드 "해 보기" 연습: 진짜 Gleo 명령(또는 1탭)으로 다음 출발이 바뀌면 완료 표시가 뜨고,
 * 끝내면 연습으로 바꾼 다음 출발을 되돌린다 (연습 때문에 실제 운행이 개인으로 기록되면 안 된다).
 */
@RunWith(RobolectricTestRunner::class)
class ChangePracticeScenarioTest {
    private lateinit var app: TestApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun()
    }

    @After
    fun tearDown() {
        scope.cancel()
        app.close()
    }

    @Test
    fun `기본 업무인 차에서 Gleo 로 '차곡에 이번 운행 개인 용무로 기록해줘'라고 하면 연습이 끝나고, 끝내면 다음 출발이 업무로 돌아온다`() = runBlocking<Unit> {
        val practice = ChangePractice(app.nextDeparture, app.settings, scope)
        practice.start(Category.BUSINESS)

        app.container.gleo.setCategory(Category.PERSONAL, target = null) // 시동 뒤 안 달렸으니 다음 출발
        val done = await(practice.state) { it?.isDone == true }!!
        await(app.settings.settings) { it.isChangeTried }
        practice.end()

        assertEquals(Category.PERSONAL, done.target)
        await(app.nextDeparture.next) { it?.category == Category.BUSINESS && !it.isChanged }
    }

    @Test
    fun `기본 개인인 차는 업무로 바꿔 보고, Gleo 가 없는 차는 토글 1탭으로 한다`() = runBlocking<Unit> {
        val practice = ChangePractice(app.nextDeparture, app.settings, scope)
        practice.start(Category.PERSONAL)

        practice.tap(Category.BUSINESS)

        assertEquals(Category.BUSINESS, await(practice.state) { it?.isDone == true }!!.target)
    }

    @Test
    fun `연습 전에 골라 둔 다음 출발은 치지 않는다`() = runBlocking<Unit> {
        app.nextDeparture.pickCategory(Category.PERSONAL)
        val practice = ChangePractice(app.nextDeparture, app.settings, scope)

        practice.start(Category.BUSINESS)

        assertFalse(await(practice.state) { it != null }!!.isDone)
    }

    @Test
    fun `첫 실행에서 기본 분류를 개인으로 바꾸고 돌아오면 연습 목표도 업무로 바뀐다`() = runBlocking<Unit> {
        val practice = ChangePractice(app.nextDeparture, app.settings, scope)
        practice.start(Category.BUSINESS)
        await(practice.state) { it?.target == Category.PERSONAL }

        practice.start(Category.PERSONAL)

        assertEquals(Category.BUSINESS, await(practice.state) { it?.target == Category.BUSINESS }!!.target)
    }

    @Test
    fun `Gleo 명령이 처음 성공하면 시작 가이드 말해 보기가 된 것으로 남는다`() = runBlocking<Unit> {
        app.container.gleo.setCategory(Category.PERSONAL, target = null)
        app.container.gleo.handle(
            GleoProtocol.SET_TRIP_CATEGORY,
            JSONObject("""{"category":"BUSINESS","trip":"NEXT"}"""),
        )

        await(app.settings.settings) { it.isChangeTried }
    }
}
