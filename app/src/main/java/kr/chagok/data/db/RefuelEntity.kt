package kr.chagok.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kr.chagok.domain.EnergyMark
import kr.chagok.domain.model.EnergyKind
import kr.chagok.domain.model.Refuel
import java.time.LocalDate
import java.time.LocalTime

/** 주유·충전 한 번 */
@Entity(tableName = "refuels", indices = [Index(value = ["uid"], unique = true)])
data class RefuelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val date: LocalDate,
    val time: LocalTime,
    val odometerKm: Double,
    val kind: EnergyKind,
    val fromPercent: Int,
    val toPercent: Int,
    val amountWon: Long?,
    val isAmountSkipped: Boolean,
) {
    fun toDomain(): Refuel = Refuel(
        id = id,
        uid = uid,
        date = date,
        time = time,
        odometerKm = odometerKm,
        kind = kind,
        fromPercent = fromPercent,
        toPercent = toPercent,
        amountWon = amountWon,
        isAmountSkipped = isAmountSkipped,
    )

    companion object {
        fun from(refuel: Refuel): RefuelEntity = RefuelEntity(
            id = refuel.id,
            uid = refuel.uid,
            date = refuel.date,
            time = refuel.time,
            odometerKm = refuel.odometerKm,
            kind = refuel.kind,
            fromPercent = refuel.fromPercent,
            toPercent = refuel.toPercent,
            amountWon = refuel.amountWon,
            isAmountSkipped = refuel.isAmountSkipped,
        )
    }
}

/** 연료·배터리마다 하나씩 두는 잔량 기준점. 시동을 끈 채 넣은 주유를 다음 시동에서 알아채려고 저장한다 */
@Entity(tableName = "energy_marks")
data class EnergyMarkEntity(
    @PrimaryKey val kind: EnergyKind,
    val percent: Int,
    val odometerKm: Double,
    val isFilling: Boolean,
) {
    fun toDomain(): EnergyMark = EnergyMark(percent = percent, odometerKm = odometerKm, isFilling = isFilling)

    companion object {
        fun from(kind: EnergyKind, mark: EnergyMark): EnergyMarkEntity =
            EnergyMarkEntity(kind = kind, percent = mark.percent, odometerKm = mark.odometerKm, isFilling = mark.isFilling)
    }
}

@Dao
interface RefuelDao {
    @Query("SELECT * FROM refuels ORDER BY id")
    fun observeAll(): Flow<List<RefuelEntity>>

    @Query("SELECT * FROM refuels WHERE id = :id")
    suspend fun find(id: Long): RefuelEntity?

    @Query("SELECT * FROM refuels WHERE kind = :kind ORDER BY id DESC LIMIT 1")
    suspend fun last(kind: EnergyKind): RefuelEntity?

    @Insert
    suspend fun insert(refuel: RefuelEntity): Long

    @Update
    suspend fun update(refuel: RefuelEntity)

    @Query("SELECT * FROM energy_marks WHERE kind = :kind")
    suspend fun mark(kind: EnergyKind): EnergyMarkEntity?

    @Upsert
    suspend fun saveMark(mark: EnergyMarkEntity)
}
