package kr.chagok.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.RecordUid
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 손으로 쓴 마이그레이션이 실제로 쓰던 DB 를 옮기는지.
 * 옛 버전 DB 는 app/schemas 의 그 버전 JSON 에 적힌 CREATE 문으로 만든다 (Room 이 만든 것과 같은 표).
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file: File = context.getDatabasePath(DB_NAME)

    @Before
    fun setUp() {
        file.parentFile?.mkdirs()
        file.delete()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `3 에서 4 로 올리면 기존 운행·사람·수정 기록마다 서로 다른 uid 가 생긴다`() = runBlocking {
        createVersion3 { db ->
            repeat(3) { i ->
                db.execSQL(
                    "INSERT INTO trips (date, personId, startKm, endKm, startedAt, endedAt, category, reason, note, isGap) " +
                        "VALUES (20000, 1, ${100 + i * 10}, ${110 + i * 10}, 32400, 34200, " +
                        "'BUSINESS', 'DEFAULT_BUSINESS', '', 0)",
                )
            }
            db.execSQL("INSERT INTO people (name, dept, initial, `order`, isHidden) VALUES ('박정훈', '영업', '박', 0, 0)")
            db.execSQL("INSERT INTO people (name, dept, initial, `order`, isHidden) VALUES ('최서연', '영업', '최', 1, 0)")
            db.execSQL(
                "INSERT INTO admin_edits (tripId, tripDate, field, fromValue, toValue, editedAt) " +
                    "VALUES (1, 20000, 'CATEGORY', 'BUSINESS', 'PERSONAL', '2026-10-26T09:00')",
            )
        }

        val db = Room.databaseBuilder(context, ChagokDatabase::class.java, DB_NAME)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val trips = db.tripDao().observeAll().first()
            val people = db.personDao().getAll()
            val edits = db.adminEditDao().observeAll().first()

            val uids = trips.map { it.uid } + people.map { it.uid } + edits.map { it.uid }
            assertEquals(6, uids.size)
            assertEquals("줄마다 다른 값", uids.size, uids.toSet().size)
            assertTrue("UUID v4 모양: $uids", uids.all(UUID_V4::matches))
            // 기존 값은 그대로
            assertEquals(listOf(110.0, 120.0, 130.0), trips.map { it.endKm })
            assertEquals(listOf("박정훈", "최서연"), people.map { it.name })
        } finally {
            db.close()
        }
    }

    @Test
    fun `옮긴 뒤 새로 넣는 운행도 uid 가 겹치지 않는다`() = runBlocking {
        createVersion3 { db ->
            db.execSQL(
                "INSERT INTO trips (date, personId, startKm, endKm, startedAt, endedAt, category, reason, note, isGap) " +
                    "VALUES (20000, NULL, 100, 110, NULL, NULL, NULL, NULL, '', 1)",
            )
        }
        val db = Room.databaseBuilder(context, ChagokDatabase::class.java, DB_NAME)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val old = db.tripDao().observeAll().first().single()
            db.tripDao().insert(old.copy(id = 0, uid = RecordUid.new()))
            // 같은 uid 를 다시 넣으려 하면 건너뛴다
            assertEquals(-1L, db.tripDao().insertIfNew(old.copy(id = 0)))

            assertEquals(2, db.tripDao().observeAll().first().map { it.uid }.toSet().size)
        } finally {
            db.close()
        }
    }

    // app/schemas/…/3.json 의 CREATE 문으로 버전 3 DB 파일을 만들고 [fill] 로 줄을 넣는다
    private fun createVersion3(fill: (SQLiteDatabase) -> Unit) {
        val schema = JSONObject(File(SCHEMA_DIR, "3.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace(TABLE_NAME, table))
                val indices = entity.optJSONArray("indices")
                for (j in 0 until (indices?.length() ?: 0)) {
                    db.execSQL(indices!!.getJSONObject(j).getString("createSql").replace(TABLE_NAME, table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            fill(db)
            db.version = 3
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
        const val TABLE_NAME = "\${TABLE_NAME}"
        val SCHEMA_DIR = File("schemas/kr.chagok.data.db.ChagokDatabase")
        val UUID_V4 = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }
}
