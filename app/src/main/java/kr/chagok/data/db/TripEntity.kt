package kr.chagok.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.Trip
import java.time.LocalDate
import java.time.LocalTime

/** 운행 한 건 = 운행기록부 한 줄. [id] 는 이 차 안에서만, [uid] 는 차 밖에서도 유일하다 */
@Entity(tableName = "trips", indices = [Index(value = ["uid"], unique = true)])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val date: LocalDate,
    val personId: Long?,
    @Embedded(prefix = "origin_") val origin: PlaceColumns?,
    @Embedded(prefix = "destination_") val destination: PlaceColumns?,
    val startKm: Double,
    val endKm: Double,
    val startedAt: LocalTime?,
    val endedAt: LocalTime?,
    val category: Category?,
    val reason: ClassifyReason?,
    val note: String,
    val isGap: Boolean,
) {
    fun toDomain(): Trip = Trip(
        id = id,
        uid = uid,
        date = date,
        personId = personId,
        origin = origin?.toDomain(),
        destination = destination?.toDomain(),
        startKm = startKm,
        endKm = endKm,
        startedAt = startedAt,
        endedAt = endedAt,
        category = category,
        reason = reason,
        note = note,
        isGap = isGap,
    )

    companion object {
        fun from(trip: Trip): TripEntity = TripEntity(
            id = trip.id,
            uid = trip.uid,
            date = trip.date,
            personId = trip.personId,
            origin = PlaceColumns.from(trip.origin),
            destination = PlaceColumns.from(trip.destination),
            startKm = trip.startKm,
            endKm = trip.endKm,
            startedAt = trip.startedAt,
            endedAt = trip.endedAt,
            category = trip.category,
            reason = trip.reason,
            note = trip.note,
            isGap = trip.isGap,
        )
    }
}

@Dao
interface TripDao {
    /** 1년 1,000건이어도 1MB 미만이라 전부 읽어 메모리에서 묶는다 */
    @Query("SELECT * FROM trips ORDER BY id")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY id")
    suspend fun all(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun find(id: Long): TripEntity?

    /** 이 사람이 쓴 운행 수 (지난 운행에 쓰인 사람은 지우지 않고 숨긴다) */
    @Query("SELECT COUNT(*) FROM trips WHERE personId = :personId")
    suspend fun countOfPerson(personId: Long): Int

    @Query("SELECT endKm FROM trips ORDER BY id DESC LIMIT 1")
    suspend fun lastEndKm(): Double?

    /** 그날 마지막으로 끝난 운행 (기록 안 된 주행은 빼고) */
    @Query("SELECT * FROM trips WHERE date = :date AND isGap = 0 ORDER BY id DESC LIMIT 1")
    suspend fun lastTripOn(date: LocalDate): TripEntity?

    @Insert
    suspend fun insert(trip: TripEntity): Long

    /** 이미 있는 [TripEntity.uid] 면 넣지 않고 -1 (같은 백업을 두 번 되살려도 운행이 겹치지 않는다) */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)
}
