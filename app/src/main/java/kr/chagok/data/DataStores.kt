package kr.chagok.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

/**
 * 앱이 쓰는 DataStore 파일. 파일 하나에 인스턴스는 하나여야 해서 여기 한 곳에서만 만든다.
 * 저장소는 DataStore 를 생성자로 받는다 (테스트는 임시 파일로 만든 것을 넣는다).
 */
internal val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

internal val Context.adminStore: DataStore<Preferences> by preferencesDataStore(name = "admin")

/**
 * 저장소가 DataStore 를 읽고 쓰는 창구. 쓰기가 끝날 때마다 읽기를 새로 시작한다.
 * DataStore(1.1.4)는 읽기를 시작하는 순간 진행 중이던 쓰기를 놓칠 수 있고, 놓친 값은 다음 쓰기 전까지 보이지 않는다.
 * 화면이 열릴 때마다 설정을 새로 읽으므로 이렇게 해야 마지막 값이 보인다.
 */
internal class PreferenceStore(private val store: DataStore<Preferences>) {
    private val writeCount = MutableStateFlow(0)

    /** 지금 값, 그리고 바뀔 때마다. 쓰기 직후에는 같은 값이 한 번 더 올 수 있다 (받는 쪽이 같은 값을 거른다) */
    @OptIn(ExperimentalCoroutinesApi::class)
    val data: Flow<Preferences> = writeCount.flatMapLatest { store.data }

    suspend fun edit(block: (MutablePreferences) -> Unit): Preferences =
        store.edit(block).also { writeCount.update { it + 1 } }
}
