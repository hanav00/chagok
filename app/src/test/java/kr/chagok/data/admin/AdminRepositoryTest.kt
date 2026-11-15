package kr.chagok.data.admin

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.model.AdminState
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.setPinWithNewKey
import kr.chagok.testing.testDataStore
import kr.chagok.testing.unreadableDataStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.Base64

/**
 * 관리자 PIN · 복구키 · 잠금.
 * 임시 파일 DataStore 에 쓰고, 같은 파일을 다시 열어 앱을 다시 켠 것을 흉내 낸다. 안드로이드 없이 돈다.
 */
class AdminRepositoryTest {
    private val directory = Files.createTempDirectory("admin-test").toFile()
    private var job = Job()
    private lateinit var store: DataStore<Preferences>
    private var admin = open()

    private fun open(): AdminRepository {
        val scope = CoroutineScope(Dispatchers.IO + job)
        store = testDataStore(scope, directory)
        return AdminRepository(store, scope)
    }

    /** 앱을 껐다 켠다: 메모리(풀림 상태)는 사라지고 디스크(해시·틀린 횟수)만 남는다 */
    private suspend fun restartApp() {
        job.cancelAndJoin()
        job = Job()
        admin = open()
    }

    @After
    fun tearDown() = runBlocking {
        job.cancelAndJoin()
        directory.deleteRecursively()
        Unit
    }

    private suspend fun stateWhere(condition: (AdminState) -> Boolean): AdminState = await(admin.state, condition)

    private suspend fun wrongTimes(times: Int) = repeat(times) { assertFalse(admin.unlock(WRONG_PIN)) }

    @Test
    fun `PIN 을 정하지 않은 차는 잠금 없이 모두 관리할 수 있고 관리자 수정으로 남지 않는다`() = runBlocking {
        val state = stateWhere { it.triesLeft == ChagokConfig.PIN_MAX_TRIES }

        assertFalse(state.hasPin)
        assertTrue(state.canManage)
        assertFalse(state.isAdminEdit)
        assertFalse(state.isPinBlocked)
    }

    @Test
    fun `관리자 정보를 읽지 못하면 PIN 없는 차로 열리지 않고 잠긴 쪽으로 본다`() = runBlocking {
        val unreadable = AdminRepository(unreadableDataStore(), CoroutineScope(Dispatchers.IO + job))

        val state = await(unreadable.state) { it == AdminState.UNREADABLE }

        assertTrue(state.hasPin)
        assertFalse(state.canManage)
        assertFalse(state.isAdminEdit)
    }

    @Test
    fun `PIN 을 처음 정하면 16자리 숫자 복구키를 돌려주고 관리자 모드로 열린다`() = runBlocking {
        val key = admin.setPinWithNewKey(PIN)

        assertEquals(16, key.length)
        assertTrue(key.all(Char::isDigit))
        val state = stateWhere { it.hasPin }
        assertTrue(state.isUnlocked)
        assertTrue(state.isAdminEdit)
        assertEquals(5, state.triesLeft)
    }

    @Test
    fun `잠그면 관리할 수 없고 맞는 PIN 으로 다시 열린다`() = runBlocking {
        admin.setPinWithNewKey(PIN)

        admin.lock()
        val locked = stateWhere { it.hasPin && !it.isUnlocked }
        assertFalse(locked.canManage)
        assertFalse(locked.isAdminEdit)

        assertTrue(admin.unlock(PIN))
        assertTrue(stateWhere { it.isUnlocked }.canManage)
    }

    @Test
    fun `앱을 다시 켜면 잠긴 채로 시작한다 (풀림은 메모리에만)`() = runBlocking {
        admin.setPinWithNewKey(PIN)
        stateWhere { it.isUnlocked }

        restartApp()

        val state = stateWhere { it.hasPin }
        assertFalse(state.isUnlocked)
        assertFalse(state.canManage)
    }

    @Test
    fun `PIN 을 틀리면 남은 횟수가 하나씩 줄고, 맞히면 다시 5번이 된다`() = runBlocking {
        admin.setPinWithNewKey(PIN)
        admin.lock()

        wrongTimes(2)
        assertEquals(3, stateWhere { it.triesLeft == 3 }.triesLeft)
        assertFalse(admin.state.value.isUnlocked)

        assertTrue(admin.unlock(PIN))
        assertEquals(5, stateWhere { it.isUnlocked }.triesLeft)
    }

    @Test
    fun `동시에 여러 번 틀려도 틀린 횟수가 빠지지 않는다`() = runBlocking {
        admin.setPinWithNewKey(PIN)
        admin.lock()

        List(3) { async(Dispatchers.Default) { admin.unlock(WRONG_PIN) } }.awaitAll()

        assertEquals(2, stateWhere { it.triesLeft == 2 }.triesLeft)
    }

    @Test
    fun `틀린 횟수는 앱을 다시 켜도 이어진다`() = runBlocking {
        admin.setPinWithNewKey(PIN)
        wrongTimes(3)

        restartApp()

        assertEquals(2, stateWhere { it.hasPin }.triesLeft)
        wrongTimes(1)
        assertEquals(1, stateWhere { it.triesLeft == 1 }.triesLeft)
    }

    @Test
    fun `5번 틀리면 PIN 이 막혀 맞는 PIN 도 거절되고, 앱을 다시 켜도 막혀 있다`() = runBlocking {
        admin.setPinWithNewKey(PIN)
        admin.lock()
        wrongTimes(5)

        assertTrue(stateWhere { it.triesLeft == 0 }.isPinBlocked)
        assertFalse(admin.unlock(PIN))

        restartApp()

        assertFalse(admin.unlock(PIN))
        val state = stateWhere { it.hasPin }
        assertTrue(state.isPinBlocked)
        assertFalse(state.isUnlocked)
        assertEquals("막힌 뒤에는 더 세지 않는다", 0, state.triesLeft)
    }

    @Test
    fun `PIN 이 막혀도 복구키를 확인하고 새 PIN 을 정하면 다시 열리고 복구키는 그대로 쓸 수 있다`() = runBlocking {
        val key = admin.setPinWithNewKey(PIN)
        admin.lock()
        wrongTimes(5)

        assertTrue(admin.checkRecoveryKey(key))
        admin.changePin(NEW_PIN)

        val state = stateWhere { it.isUnlocked }
        assertFalse(state.isPinBlocked)
        assertEquals(5, state.triesLeft)
        admin.lock()
        assertTrue(admin.unlock(NEW_PIN))
        assertTrue(admin.checkRecoveryKey(key))
    }

    @Test
    fun `틀린 복구키는 거절되고 PIN 을 정한 적 없으면 어떤 복구키도 거절된다`() = runBlocking {
        assertFalse(admin.checkRecoveryKey("0000000000000000"))

        val key = admin.setPinWithNewKey(PIN)

        val wrongKey = key.dropLast(1) + ((key.last().digitToInt() + 1) % 10)
        assertFalse(admin.checkRecoveryKey(wrongKey))
        assertFalse("PIN 은 복구키가 아니다", admin.checkRecoveryKey(PIN))
    }

    @Test
    fun `PIN 만 바꾸면 복구키는 그대로이고 예전 PIN 은 더 쓸 수 없다`() = runBlocking {
        val key = admin.setPinWithNewKey(PIN)

        admin.changePin(NEW_PIN)
        admin.lock()

        assertFalse(admin.unlock(PIN))
        assertTrue(admin.unlock(NEW_PIN))
        assertTrue(admin.checkRecoveryKey(key))
    }

    @Test
    fun `관리자를 넘기면 새 복구키가 나오고 예전 PIN·복구키는 더 쓸 수 없다`() = runBlocking {
        val oldKey = admin.setPinWithNewKey(PIN)

        val newKey = admin.setPinWithNewKey(NEW_PIN)
        admin.lock()

        assertNotEquals(oldKey, newKey)
        assertFalse(admin.checkRecoveryKey(oldKey))
        assertTrue(admin.checkRecoveryKey(newKey))
        assertFalse(admin.unlock(PIN))
        assertTrue(admin.unlock(NEW_PIN))
    }

    @Test
    fun `새 복구키를 만들기만 하면 저장하지 않고, 함께 저장해야 예전 PIN·복구키가 바뀐다`() = runBlocking {
        val oldKey = admin.setPinWithNewKey(PIN)
        admin.lock()

        val newKey = admin.newRecoveryKey()
        // 화면에서 "적어 뒀어요"를 누르기 전 (시트가 닫히면 여기서 끝난다)
        assertTrue(admin.checkRecoveryKey(oldKey))
        assertFalse(admin.checkRecoveryKey(newKey))
        assertTrue(admin.unlock(PIN))

        admin.setPinAndKey(NEW_PIN, newKey)
        admin.lock()
        assertFalse(admin.checkRecoveryKey(oldKey))
        assertTrue(admin.checkRecoveryKey(newKey))
        assertTrue(admin.unlock(NEW_PIN))
    }

    @Test
    fun `관리자 없음으로 되돌리면 PIN·복구키·틀린 횟수가 모두 지워지고 잠금 없이 관리한다`() = runBlocking {
        val key = admin.setPinWithNewKey(PIN)
        wrongTimes(2)

        admin.clear()

        val state = stateWhere { !it.hasPin }
        assertTrue(state.canManage)
        assertFalse(state.isUnlocked)
        assertEquals(5, state.triesLeft)
        assertFalse(admin.checkRecoveryKey(key))
        assertTrue(store.data.first().asMap().isEmpty())
    }

    @Test
    fun `PIN·복구키 원문은 저장하지 않고 솔트를 붙인 해시만 남는다`() = runBlocking {
        val key = admin.setPinWithNewKey(PIN)

        val saved = store.data.first().asMap().mapKeys { it.key.name }
        assertEquals(setOf("pin_salt", "pin_hash", "recovery_key_salt", "recovery_key_hash", "wrong_tries"), saved.keys)
        saved.values.forEach { value ->
            assertNotEquals(PIN, value.toString())
            assertFalse("복구키 원문이 없다", value.toString().contains(key))
        }
        val fileBytes = directory.walk().filter { it.isFile }.map { it.readText(Charsets.ISO_8859_1) }.joinToString()
        assertFalse("파일 어디에도 복구키 원문이 없다", fileBytes.contains(key))

        // 저장된 값은 저장된 솔트로 PIN 을 해시한 것과 같다
        val salt = decoded(saved.getValue("pin_salt"))
        assertArrayEquals(SecretHasher.hash(PIN, salt), decoded(saved.getValue("pin_hash")))
    }

    @Test
    fun `같은 PIN 으로 다시 정해도 솔트가 새로 바뀌어 저장된 해시가 달라진다`() = runBlocking {
        admin.setPinWithNewKey(PIN)
        val before = store.data.first()[PIN_HASH_KEY]

        admin.changePin(PIN)

        assertNotEquals(before, store.data.first()[PIN_HASH_KEY])
        admin.lock()
        assertTrue(admin.unlock(PIN))
    }

    private fun decoded(value: Any): ByteArray = Base64.getDecoder().decode(value as String)

    private companion object {
        const val PIN = "4826"
        const val NEW_PIN = "2580"
        const val WRONG_PIN = "0000"
        val PIN_HASH_KEY = stringPreferencesKey("pin_hash")
    }
}
