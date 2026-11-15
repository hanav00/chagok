package kr.chagok.data.admin

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kr.chagok.data.PreferenceStore
import kr.chagok.data.writeOrThrow
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.model.AdminState
import java.io.IOException
import java.util.Base64

/**
 * 관리자 PIN · 복구키 · 잠금. 서버 없이 차 안에서만 권한을 나누며, 관리자는 1명(PIN 하나)이고 운전자는 인증 없이 탄다.
 * PIN·복구키는 솔트 해시만 디스크에 두고 틀린 횟수도 남긴다. 풀림 상태는 메모리에만 두고 출발하면 기록기가 [lock] 한다.
 * 복구키(16자리 숫자)는 PIN 을 정하거나 관리자를 넘길 때 새로 만들어 한 번만 돌려준다.
 *
 * TODO: 해시를 Android Keystore 키로 한 번 더 감쌀지 정한다
 */
class AdminRepository(store: DataStore<Preferences>, scope: CoroutineScope) {
    private val prefs = PreferenceStore(store)
    private val isUnlocked = MutableStateFlow(false)

    /**
     * 읽기 실패는 [AdminState.UNREADABLE] (잠김). 빈 값으로 대신하면 "PIN 없음"이 되어 운전자에게 관리 화면이 열리고,
     * 그 상태에서 PIN 처음 정하기로 관리자 PIN 을 덮어쓸 수도 있다.
     * 첫 읽기 전(앱 시작 몇 ms)의 처음 값은 "PIN 없음"이다. 사람이 그사이 관리 화면까지 갈 수 없어 그대로 둔다.
     * TODO: 파일이 깨진 경우(CorruptionException)는 계속 잠긴다. 복구 방법(초기화 정책)을 정한다
     */
    val state: StateFlow<AdminState> = combine(
        prefs.data
            .map<Preferences, Preferences?> { it }
            .catch { error ->
                if (error !is IOException) throw error
                Log.e(TAG, "관리자 정보 읽기 실패", error)
                emit(null)
            },
        isUnlocked,
    ) { prefs, unlocked ->
        if (prefs == null) return@combine AdminState.UNREADABLE
        val hasPin = prefs[Keys.PIN_HASH] != null
        AdminState(
            hasPin = hasPin,
            isUnlocked = hasPin && unlocked,
            triesLeft = ChagokConfig.PIN_MAX_TRIES - (prefs[Keys.WRONG_TRIES] ?: 0),
        )
    }.stateIn(scope, SharingStarted.Eagerly, AdminState())

    /**
     * 맞으면 관리자 모드로 열고 true. 틀리면 횟수를 센다. 너무 많이 틀렸으면 PIN 으로는 열지 않는다.
     * 읽기·판단·횟수 쓰기를 한 번의 저장 안에서 해서, 동시에 두 번 틀려도 횟수가 빠지지 않는다.
     */
    suspend fun unlock(pin: String): Boolean {
        var isMatch = false
        update { prefs ->
            val wrongTries = prefs[Keys.WRONG_TRIES] ?: 0
            if (wrongTries >= ChagokConfig.PIN_MAX_TRIES) return@update
            isMatch = prefs.matches(pin, Keys.PIN_SALT, Keys.PIN_HASH)
            prefs[Keys.WRONG_TRIES] = if (isMatch) 0 else wrongTries + 1
        }
        if (isMatch) isUnlocked.value = true
        return isMatch
    }

    fun lock() {
        isUnlocked.value = false
    }

    suspend fun checkRecoveryKey(key: String): Boolean =
        prefs.data.first().matches(key, Keys.KEY_SALT, Keys.KEY_HASH)

    /**
     * 새 복구키를 만들기만 한다 (저장하지 않음).
     * 화면은 이 키를 먼저 보여 주고, 사용자가 "적어 뒀어요"를 누른 뒤에 [setPinAndKey] 로 저장한다.
     * 그 전에 시트가 닫히면 예전 PIN·복구키가 그대로 남아 키를 놓쳐도 잠기지 않는다.
     */
    fun newRecoveryKey(): String = SecretHasher.newDigits(ChagokConfig.RECOVERY_KEY_LENGTH)

    /** 새 PIN + 새 복구키를 함께 저장하고 관리자 모드로 연다. 예전 PIN·복구키는 더 쓸 수 없다 */
    suspend fun setPinAndKey(pin: String, key: String) {
        update {
            it.writeSecret(pin, Keys.PIN_SALT, Keys.PIN_HASH)
            it.writeSecret(key, Keys.KEY_SALT, Keys.KEY_HASH)
            it[Keys.WRONG_TRIES] = 0
        }
        isUnlocked.value = true
    }

    /** PIN 바꾸기 · 복구키로 다시 정하기: 복구키는 그대로 둔다 */
    suspend fun changePin(pin: String) {
        update {
            it.writeSecret(pin, Keys.PIN_SALT, Keys.PIN_HASH)
            it[Keys.WRONG_TRIES] = 0
        }
        isUnlocked.value = true
    }

    /** 관리자 없음으로 되돌린다 (PIN·복구키·틀린 횟수를 모두 지운다) */
    suspend fun clear() {
        update { it.clear() }
        isUnlocked.value = false
    }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        writeOrThrow(TAG, "관리자 정보 저장") { prefs.edit(block) }
    }

    private fun Preferences.matches(
        secret: String,
        saltKey: Preferences.Key<String>,
        hashKey: Preferences.Key<String>,
    ): Boolean {
        val salt = this[saltKey]?.let(::decode) ?: return false
        val hash = this[hashKey]?.let(::decode) ?: return false
        return SecretHasher.matches(secret, salt, hash)
    }

    private fun MutablePreferences.writeSecret(
        secret: String,
        saltKey: Preferences.Key<String>,
        hashKey: Preferences.Key<String>,
    ) {
        val salt = SecretHasher.newSalt()
        this[saltKey] = encode(salt)
        this[hashKey] = encode(SecretHasher.hash(secret, salt))
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private object Keys {
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_SALT = stringPreferencesKey("recovery_key_salt")
        val KEY_HASH = stringPreferencesKey("recovery_key_hash")
        val WRONG_TRIES = intPreferencesKey("wrong_tries")
    }

    private companion object {
        const val TAG = "AdminRepository"
    }
}
