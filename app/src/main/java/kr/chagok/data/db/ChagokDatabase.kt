package kr.chagok.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** 차 안 SQLite. 앱 전용 저장소에 두고 안드로이드 파일 단위 암호화를 따른다 */
@Database(
    entities = [
        TripEntity::class,
        ActiveTripEntity::class,
        PersonEntity::class,
        AdminEditEntity::class,
        RefuelEntity::class,
        EnergyMarkEntity::class,
    ],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // 사람 숨기기 (people.isHidden)
        AutoMigration(from = 2, to = 3), // 주행 중 음성으로 분류 바꿈 (active_trip.isEditedWhileDriving)
        AutoMigration(from = 4, to = 5), // 주유·충전 기록 (refuels, energy_marks)
    ],
)
@TypeConverters(Converters::class)
abstract class ChagokDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun activeTripDao(): ActiveTripDao
    abstract fun personDao(): PersonDao
    abstract fun adminEditDao(): AdminEditDao
    abstract fun refuelDao(): RefuelDao

    companion object {
        const val FILE_NAME = "chagok.db"

        fun open(context: Context): ChagokDatabase =
            Room.databaseBuilder(context, ChagokDatabase::class.java, FILE_NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
