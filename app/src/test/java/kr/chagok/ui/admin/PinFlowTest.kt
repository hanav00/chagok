package kr.chagok.ui.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kr.chagok.domain.ChagokConfig
import kr.chagok.testing.DEMO_PIN
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.ViewModelTestRule
import kr.chagok.testing.setUpLockedAdmin
import kr.chagok.testing.setUpUnlockedAdmin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 관리자 PIN 시트 단계 흐름. 5번 틀리면 복구키로만 열 수 있다.
 * 새 복구키는 "적어 뒀어요"를 눌러야 저장되고, 그 전에 시트를 닫으면 예전 PIN·복구키가 그대로다.
 */
@RunWith(RobolectricTestRunner::class)
class PinFlowTest {
    @get:Rule
    val rule = ViewModelTestRule()
    private val app get() = rule.app
    private val admin get() = app.container.admin

    /** 시트 하나를 연 것 */
    private inner class Sheet(mode: PinMode) {
        private val job = SupervisorJob()
        var finishCount = 0
            private set

        // 시트는 화면의 코루틴 범위(메인 스레드)에서 확인·저장을 돌린다
        val flow = PinFlow(mode, admin, app.container.messages, CoroutineScope(Dispatchers.Main + job)) {
            finishCount++
        }

        /** 숫자판을 누르고, 확인(해시 비교·저장)이 끝날 때까지 기다린다 */
        suspend fun enter(digits: String) {
            rule.onMain { digits.forEach(flow::press) }
            job.children.toList().joinAll()
        }

        /** 숫자판 밖 버튼 (지우기·PIN을 잊었어요·확인·적어 뒀어요). 누른 뒤 확인·저장이 끝날 때까지 기다린다 */
        suspend fun tap(action: PinFlow.() -> Unit) {
            rule.onMain { flow.action() }
            job.children.toList().joinAll()
        }
    }

    private fun wrongPin() = "0000".also { check(it != DEMO_PIN) }

    @Test
    fun `잠긴 차에서 맞는 PIN → 관리자 모드로 열리고 시트가 닫힌다`() = runBlocking<Unit> {
        app.setUpLockedAdmin()
        val sheet = Sheet(PinMode.UNLOCK)
        assertEquals(PinStage.PIN, sheet.flow.stage)

        sheet.enter(DEMO_PIN)

        assertEquals(1, sheet.finishCount)
        await(admin.state) { it.isUnlocked }
    }

    @Test
    fun `틀린 PIN → 빨간 줄, 입력이 비고, 남은 횟수 4, 다음 숫자를 누르면 빨간 줄이 사라진다`() = runBlocking<Unit> {
        app.setUpLockedAdmin()
        val sheet = Sheet(PinMode.UNLOCK)

        sheet.enter(wrongPin())

        assertEquals(PinStage.PIN, sheet.flow.stage)
        assertEquals(PinMessage.WRONG, sheet.flow.message)
        assertEquals("", sheet.flow.input)
        assertEquals(0, sheet.finishCount)
        assertFalse(await(admin.state) { it.triesLeft == ChagokConfig.PIN_MAX_TRIES - 1 }.isUnlocked)

        sheet.tap { press('1') }
        assertNull(sheet.flow.message)
    }

    @Test
    fun `5번 틀림 → 복구키로만 열 수 있다, 맞는 PIN 도 받지 않는다`() = runBlocking<Unit> {
        app.setUpLockedAdmin()
        val sheet = Sheet(PinMode.UNLOCK)
        repeat(ChagokConfig.PIN_MAX_TRIES) { sheet.enter(wrongPin()) }
        await(admin.state) { it.isPinBlocked }

        sheet.enter(DEMO_PIN)

        assertEquals(PinMessage.WRONG, sheet.flow.message)
        assertEquals(0, sheet.finishCount)
        assertFalse(admin.state.value.isUnlocked)
    }

    @Test
    fun `지우기 → 마지막 숫자만 지운다`() = runBlocking<Unit> {
        app.setUpLockedAdmin()
        val sheet = Sheet(PinMode.UNLOCK)

        sheet.tap { press('2') }
        sheet.tap { press('5') }
        sheet.tap { delete() }

        assertEquals("2", sheet.flow.input)
    }

    @Test
    fun `PIN을 잊었어요 → 복구키 16자리 칸, 틀린 복구키는 빨간 줄`() = runBlocking<Unit> {
        app.setUpLockedAdmin()
        val sheet = Sheet(PinMode.UNLOCK)

        sheet.tap { startRecovery() }
        assertEquals(PinStage.RECOVER, sheet.flow.stage)
        assertEquals(16, sheet.flow.stage.inputLength)

        sheet.enter("1".repeat(16))
        assertTrue("16자리를 다 넣으면 확인 버튼이 켜진다", sheet.flow.isRecoveryFilled)
        assertNull("확인을 누르기 전에는 판단하지 않는다", sheet.flow.message)

        sheet.tap { submitRecovery() }

        assertEquals(PinStage.RECOVER, sheet.flow.stage)
        assertEquals(PinMessage.RECOVER_WRONG, sheet.flow.message)
        assertEquals("틀린 곳만 고치게 넣은 숫자는 남는다", "1".repeat(16), sheet.flow.input)
    }

    @Test
    fun `5번 틀린 뒤 복구키 → 새 PIN 두 번 → 열린다, 복구키는 그대로, 예전 PIN 은 안 된다`() = runBlocking<Unit> {
        val key = app.setUpLockedAdmin()
        val sheet = Sheet(PinMode.UNLOCK)
        repeat(ChagokConfig.PIN_MAX_TRIES) { sheet.enter(wrongPin()) }

        sheet.tap { startRecovery() }
        sheet.enter(key)
        sheet.tap { submitRecovery() }
        assertEquals(PinStage.NEW, sheet.flow.stage)
        sheet.enter("1357")
        assertEquals(PinStage.CONFIRM, sheet.flow.stage)
        sheet.enter("1357")

        assertEquals("복구키를 새로 보여 주지 않고 바로 닫힌다", 1, sheet.finishCount)
        assertEquals("", sheet.flow.newKey)
        await(admin.state) { it.isUnlocked && it.triesLeft == ChagokConfig.PIN_MAX_TRIES } // 틀린 횟수도 처음으로
        assertTrue("복구키는 그대로", admin.checkRecoveryKey(key))
        admin.lock()
        assertFalse(admin.unlock(DEMO_PIN))
        assertTrue(admin.unlock("1357"))
    }

    @Test
    fun `처음 정하기 → 새 PIN 두 번 → 16자리 복구키를 보여 주고, 적어 뒀어요를 눌러야 닫힌다`() = runBlocking<Unit> {
        val sheet = Sheet(PinMode.SETUP)
        assertEquals(PinStage.NEW, sheet.flow.stage)

        sheet.enter("2468")
        sheet.enter("2468")

        assertEquals(PinStage.KEY, sheet.flow.stage)
        val key = sheet.flow.newKey
        assertEquals(ChagokConfig.RECOVERY_KEY_LENGTH, key.length)
        assertTrue("숫자만", key.all(Char::isDigit))
        assertEquals(0, sheet.finishCount)
        assertFalse("적어 뒀어요를 누르기 전에는 저장하지 않는다", admin.state.value.hasPin)

        sheet.tap { confirmKeySaved() }

        assertEquals(1, sheet.finishCount)
        await(admin.state) { it.hasPin && it.isUnlocked }
        assertTrue(admin.checkRecoveryKey(key))
    }

    @Test
    fun `처음 정하기에서 복구키를 보고 그냥 닫으면 → PIN 이 정해지지 않는다`() = runBlocking<Unit> {
        val sheet = Sheet(PinMode.SETUP)

        sheet.enter("2468")
        sheet.enter("2468")
        assertEquals(PinStage.KEY, sheet.flow.stage)

        assertFalse(admin.state.value.hasPin)
        assertEquals(0, sheet.finishCount)
    }

    @Test
    fun `처음 정하기에서 두 번이 다름 → 처음부터 다시 (빨간 줄), 저장하지 않는다`() = runBlocking<Unit> {
        val sheet = Sheet(PinMode.SETUP)

        sheet.enter("2468")
        sheet.enter("8642")

        assertEquals(PinStage.NEW, sheet.flow.stage)
        assertEquals(PinMessage.MISMATCH, sheet.flow.message)
        assertFalse(admin.state.value.hasPin)

        sheet.enter("1111")
        assertEquals(PinStage.CONFIRM, sheet.flow.stage)
        assertNull(sheet.flow.message)
        sheet.enter("1111")
        assertEquals(PinStage.KEY, sheet.flow.stage)
    }

    @Test
    fun `관리자 넘기기 → 새 복구키, 예전 PIN·복구키는 더 쓸 수 없다`() = runBlocking<Unit> {
        val oldKey = app.setUpUnlockedAdmin()
        val sheet = Sheet(PinMode.HANDOVER)

        sheet.enter("9753")
        sheet.enter("9753")

        val newKey = sheet.flow.newKey
        assertEquals(PinStage.KEY, sheet.flow.stage)
        assertEquals(ChagokConfig.RECOVERY_KEY_LENGTH, newKey.length)
        assertTrue("적어 뒀어요 전에는 예전 복구키가 그대로", admin.checkRecoveryKey(oldKey))

        sheet.tap { confirmKeySaved() }

        assertFalse(admin.checkRecoveryKey(oldKey))
        assertTrue(admin.checkRecoveryKey(newKey))
        admin.lock()
        assertFalse(admin.unlock(DEMO_PIN))
        assertTrue(admin.unlock("9753"))
    }

    @Test
    fun `PIN 바꾸기 → 새 PIN 두 번이면 닫힌다, 복구키는 그대로`() = runBlocking<Unit> {
        val key = app.setUpUnlockedAdmin()
        val sheet = Sheet(PinMode.CHANGE)

        sheet.enter("4321")
        sheet.enter("4321")

        assertEquals(1, sheet.finishCount)
        assertTrue(admin.checkRecoveryKey(key))
        admin.lock()
        assertTrue(admin.unlock("4321"))
    }
}
