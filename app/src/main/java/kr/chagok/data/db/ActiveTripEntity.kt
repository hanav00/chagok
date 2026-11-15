package kr.chagok.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kr.chagok.domain.model.ActiveTrip
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.NextDeparture
import java.time.LocalDate
import java.time.LocalTime

/** 달리고 있는 운행 (한 줄뿐). 주차하면 [TripEntity] 로 옮기고 지운다 */
@Entity(tableName = "active_trip")
data class ActiveTripEntity(
    @PrimaryKey val slot: Int = ONLY_SLOT,
    val date: LocalDate,
    val startKm: Double,
    val startedAt: LocalTime,
    val lastKm: Double,
    val lastAt: LocalTime,
    val category: Category,
    val isCategoryChanged: Boolean,
    val personId: Long?,
    @Embedded(prefix = "origin_") val origin: PlaceColumns?,
    @Embedded(prefix = "destination_") val destination: PlaceColumns?,
    @ColumnInfo(defaultValue = "0") val isEditedWhileDriving: Boolean = false,
) {
    fun toDomain(): ActiveTrip = ActiveTrip(
        date = date,
        startKm = startKm,
        startedAt = startedAt,
        lastKm = lastKm,
        lastAt = lastAt,
        departure = NextDeparture(category, isCategoryChanged, personId),
        origin = origin?.toDomain(),
        destination = destination?.toDomain(),
        isEditedWhileDriving = isEditedWhileDriving,
    )

    companion object {
        const val ONLY_SLOT = 0

        fun from(trip: ActiveTrip): ActiveTripEntity = ActiveTripEntity(
            date = trip.date,
            startKm = trip.startKm,
            startedAt = trip.startedAt,
            lastKm = trip.lastKm,
            lastAt = trip.lastAt,
            category = trip.departure.category,
            isCategoryChanged = trip.departure.isChanged,
            personId = trip.departure.personId,
            origin = PlaceColumns.from(trip.origin),
            destination = PlaceColumns.from(trip.destination),
            isEditedWhileDriving = trip.isEditedWhileDriving,
        )
    }
}

@Dao
interface ActiveTripDao {
    @Query("SELECT * FROM active_trip WHERE slot = ${ActiveTripEntity.ONLY_SLOT}")
    suspend fun find(): ActiveTripEntity?

    @Upsert
    suspend fun save(trip: ActiveTripEntity)

    @Query("UPDATE active_trip SET lastKm = :km, lastAt = :at WHERE slot = ${ActiveTripEntity.ONLY_SLOT}")
    suspend fun checkpoint(km: Double, at: LocalTime)

    /** 지금 달리는(또는 마감 못 한) 운행에 이 사람이 타고 있는지 */
    @Query("SELECT COUNT(*) FROM active_trip WHERE personId = :personId")
    suspend fun countOfPerson(personId: Long): Int

    @Query("DELETE FROM active_trip")
    suspend fun clear()
}
