package kr.chagok.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kr.chagok.testing.testDataStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files

/**
 * 화면이 설정을 읽기 시작하는 순간에 마침 저장 중이어도 저장한 값이 화면에 보인다.
 * DataStore 를 그대로 읽으면 이 경우 저장한 값을 놓치고 다음 저장까지 옛 값에 머문다.
 * 겹치는 순간은 우연이라 여러 번 겹쳐 보고, 한 번도 놓치지 않아야 한다.
 */
@RunWith(RobolectricTestRunner::class)
class PreferenceStoreTest {
    private val flag = booleanPreferencesKey("flag")

    @Test
    fun `읽기 시작과 저장이 겹쳐도 → 저장한 값이 보인다 (100번 겹쳐 봄)`() = runBlocking<Unit> {
        var missed = 0
        repeat(ROUNDS) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val directory = Files.createTempDirectory("preference-store").toFile()
            val store = PreferenceStore(testDataStore(scope, directory))
            store.edit { it[flag] = true }

            val saving = scope.launch { store.edit { it[flag] = false } }
            val shown = scope.async { withTimeoutOrNull(WAIT_MILLIS) { store.data.first { it[flag] == false } } }
            saving.join()
            if (shown.await() == null) missed++

            scope.cancel()
            directory.deleteRecursively()
        }
        assertEquals(0, missed)
    }

    private companion object {
        const val ROUNDS = 100
        const val WAIT_MILLIS = 2_000L
    }
}
