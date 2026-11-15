package kr.chagok.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 손으로 쓰는 DB 마이그레이션. 열만 더하는 것은 [ChagokDatabase] 의 AutoMigration 으로 한다.
 * 여기에는 기존 줄마다 다른 값을 채워야 하는 것만 둔다.
 */
object Migrations {
    /**
     * 3 → 4: 운행·사람·관리자 수정 기록에 전역 식별값(uid, UUID)을 더한다.
     * 기존 줄은 SQLite 난수로 UUID v4 모양을 만들어 채운 뒤 유일 색인을 건다
     * (AutoMigration 은 모든 줄에 같은 기본값을 넣어 유일 색인을 만들 수 없다).
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            UID_TABLES.forEach { table ->
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `$table` SET `uid` = $SQL_UUID_V4")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uid` ON `$table` (`uid`)")
            }
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_3_4)

    private val UID_TABLES = listOf("trips", "people", "admin_edits")

    // 줄마다 새로 계산된다: xxxxxxxx-xxxx-4xxx-[89ab]xxx-xxxxxxxxxxxx
    private const val SQL_UUID_V4 =
        "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
            "substr(lower(hex(randomblob(2))), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || " +
            "substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"
}
