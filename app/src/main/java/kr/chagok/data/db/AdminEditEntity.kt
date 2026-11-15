package kr.chagok.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kr.chagok.domain.model.AdminEdit
import kr.chagok.domain.model.RecordUid
import java.time.LocalDate
import java.time.LocalDateTime

/** 관리자 수정 기록. 쌓기만 하고 지우지 않는다 (DAO 에 삭제가 없다) */
@Entity(tableName = "admin_edits", indices = [Index(value = ["uid"], unique = true)])
data class AdminEditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 차 밖에서도 유일한 식별값 ([RecordUid]). 쌓기만 하니 넣을 때 정한다 */
    val uid: String = RecordUid.new(),
    val tripId: Long,
    val tripDate: LocalDate,
    val field: AdminEdit.Field,
    val fromValue: String?,
    val toValue: String?,
    val editedAt: LocalDateTime,
) {
    fun toDomain(): AdminEdit = AdminEdit(tripId, tripDate, field, fromValue, toValue, editedAt)

    companion object {
        fun from(edit: AdminEdit): AdminEditEntity = AdminEditEntity(
            tripId = edit.tripId,
            tripDate = edit.tripDate,
            field = edit.field,
            fromValue = edit.fromValue,
            toValue = edit.toValue,
            editedAt = edit.editedAt,
        )
    }
}

@Dao
interface AdminEditDao {
    /** 최근 것 먼저 */
    @Query("SELECT * FROM admin_edits ORDER BY id DESC")
    fun observeAll(): Flow<List<AdminEditEntity>>

    /** 이 사람이 나오는 관리자 수정 기록 수 (운행에서 빠졌어도 기록에 이름이 남아야 한다) */
    @Query("SELECT COUNT(*) FROM admin_edits WHERE field = 'PERSON' AND (fromValue = :personId OR toValue = :personId)")
    suspend fun countOfPerson(personId: String): Int

    @Insert
    suspend fun insert(edit: AdminEditEntity)
}
