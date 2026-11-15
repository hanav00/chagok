package kr.chagok.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kr.chagok.data.db.ChagokDatabase
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Robolectric 테스트용 메모리 DB. 테스트가 끝나면 close() */
fun inMemoryDatabase(): ChagokDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChagokDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/** 테스트마다 새 파일에 쓰는 DataStore (파일 하나에 인스턴스 하나 규칙 때문에 테스트끼리 나눈다) */
fun testDataStore(scope: CoroutineScope, directory: File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(scope = scope) { File(directory, "test.preferences_pb") }

/** 읽기·쓰기가 늘 실패하는 DataStore (디스크 오류·깨진 파일 흉내) */
fun unreadableDataStore(): DataStore<Preferences> = object : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw IOException("test: 읽기 실패") }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        throw IOException("test: 쓰기 실패")
}

/**
 * 앱을 껐다 켜도 남는 저장소: 운행 DB 와 설정·관리자 DataStore 파일 자리.
 * 같은 [TestDisk] 로 새 [TestApp] 을 만들면 "꺼졌다 다시 켠 앱"이 된다 ([TestApp.restart]).
 */
class TestDisk : Closeable {
    val database: ChagokDatabase = inMemoryDatabase()
    val directory: File = Files.createTempDirectory("chagok-test").toFile()

    override fun close() {
        database.close()
        directory.deleteRecursively()
    }
}
