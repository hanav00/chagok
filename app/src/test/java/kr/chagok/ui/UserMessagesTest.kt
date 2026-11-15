package kr.chagok.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.chagok.R
import kr.chagok.domain.ChagokConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** 화면 아래 알림 줄로 가는 큐와 저장 실패의 "다시 하기" */
@OptIn(ExperimentalCoroutinesApi::class)
class UserMessagesTest {
    private val messages = UserMessages()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class SavingViewModel : ViewModel()

    @Test
    fun `받는 쪽이 없을 때 쌓인 알림은 순서대로 남고 넘치면 오래된 것부터 버린다`() = runTest {
        val texts = List(ChagokConfig.NOTICE_QUEUE_SIZE + 1) { it }
        texts.forEach { messages.show(it) }

        val received = messages.messages.take(ChagokConfig.NOTICE_QUEUE_SIZE).toList().map { it.text }

        assertEquals(texts.drop(1), received)
    }

    @Test
    fun `저장이 실패하면 다시 하기가 붙은 알림이 가고, 누르면 같은 일을 다시 한다`() = runTest {
        var tries = 0
        SavingViewModel().launchSaving(messages) {
            tries++
            if (tries == 1) error("디스크가 가득 참")
        }

        val failed = messages.messages.first()
        assertEquals(R.string.error_save_retryable, failed.text)
        assertNotNull(failed.retry)

        failed.retry!!.invoke()
        assertEquals("다시 하기로 한 번 더 돌았다", 2, tries)
    }

    @Test
    fun `다시 하기를 따로 주면 그쪽을 부른다 (두 번 누름을 막는 함수로)`() = runTest {
        var retried = 0
        SavingViewModel().launchSaving(messages, retry = { retried++ }) { error("실패") }

        messages.messages.first().retry!!.invoke()

        assertEquals(1, retried)
    }

    @Test
    fun `저장이 되면 아무 알림도 없다`() = runTest {
        SavingViewModel().launchSaving(messages) { }
        // 뒤에 넣은 알림이 맨 앞에 나오면 저장 쪽은 아무것도 넣지 않은 것이다
        messages.show(R.string.voice_not_heard)

        val next = messages.messages.first()
        assertEquals(R.string.voice_not_heard, next.text)
        assertNull("음성 안내에는 다시 하기가 없다", next.retry)
    }
}
