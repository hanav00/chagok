package kr.chagok.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.Executors

/**
 * 화면(ViewModel) 테스트 준비: 테스트마다 새 [TestApp] 과 메인 스레드 하나를 두고, 끝나면 만든 ViewModel 을 정리한다.
 *
 * Main 은 스레드 하나에서 차례로 도는 디스패처로 바꾼다. Robolectric 의 Main 은 테스트 스레드의 Looper 라서
 * runBlocking 으로 기다리는 동안 거기 올린 일이 돌지 못한다. UnconfinedTestDispatcher 는 ViewModel 의 수집이
 * Room·DataStore 스레드에서 그대로 이어 돌다 DataStore 의 새 값을 놓칠 수 있어 쓰지 않는다.
 *
 * runTest 가 아니라 runBlocking 으로 돌린다. 저장소는 진짜 스레드에서 도는데 runTest 의 withTimeout 은 가상 시간이라
 * [TestApp.await] 가 기다리지 않고 바로 시간 초과가 된다. 가상 시간이 필요한 타이머만 [timerContext] 에서 센다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTestRule : TestRule {
    private val scheduler = TestCoroutineScheduler()
    private val viewModels = ViewModelStore()
    private var viewModelCount = 0
    private var currentApp: TestApp? = null

    val app: TestApp get() = checkNotNull(currentApp) { "테스트 밖에서는 앱이 없다" }

    /** 가상 시간으로 세는 타이머 자리 (HomeViewModel 의 "다시 보기" 6초) */
    val timerContext: TestDispatcher = UnconfinedTestDispatcher(scheduler)

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            ShadowLog.stream = System.out // 앱이 남긴 Log.e 를 테스트 보고서에서 보이게
            val main = Executors.newSingleThreadExecutor { Thread(it, MAIN_THREAD_NAME) }.asCoroutineDispatcher()
            Dispatchers.setMain(main)
            currentApp = TestApp()
            try {
                base.evaluate()
            } finally {
                // ViewModel 을 먼저 정리해야 닫힌 DB·DataStore 를 계속 읽으려 하지 않는다
                viewModels.clear()
                currentApp?.close()
                currentApp = null
                Dispatchers.resetMain()
                main.close()
            }
        }
    }

    /** ViewModel 을 만들어 테스트 끝에 정리되게 맡긴다: `rule.viewModel { HomeViewModel(app.container) }` */
    inline fun <reified VM : ViewModel> viewModel(noinline create: () -> VM): VM = keep(VM::class.java, create)

    fun <VM : ViewModel> keep(type: Class<VM>, create: () -> VM): VM {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(create())!!
        }
        return ViewModelProvider(viewModels, factory)["vm-${viewModelCount++}", type]
    }

    /**
     * 화면에서 누른 것처럼 메인 스레드에서 [block] 을 돌린다. 테스트 스레드에서 바로 부르면
     * 메인 스레드에 이미 줄 서 있던 일 사이에 끼어들 수 있다.
     */
    suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    /** [timerContext] 의 가상 시간을 [millis] 만큼 앞으로 돌리고, 그 시각까지 할 일을 다 돌린다 */
    fun advanceTimeBy(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    private companion object {
        const val MAIN_THREAD_NAME = "test-main"
    }
}
