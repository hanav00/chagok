package kr.chagok.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.RecordUid

/**
 * 이 차를 쓰는 사람. [order] 순서대로 보여 주고, 숨기지 않은 첫 사람이 기본 사용자다.
 * @property uid 차 밖에서도 유일한 식별값 ([RecordUid]). 여러 차 기록을 합칠 때 같은 사람인지 가린다
 * @property isHidden 지난 운행에 쓰여 지우지 못하고 숨긴 사람 (기록부에는 이름이 남고 고르는 목록에서만 빠진다)
 */
@Entity(tableName = "people", indices = [Index(value = ["uid"], unique = true)])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = RecordUid.new(),
    val name: String,
    val dept: String,
    val initial: String,
    val order: Int,
    @ColumnInfo(defaultValue = "0") val isHidden: Boolean = false,
) {
    fun toDomain(): Person = Person(id = id, name = name, dept = dept, initial = initial, isHidden = isHidden)
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY `order`")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people ORDER BY `order`")
    suspend fun getAll(): List<PersonEntity>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun get(id: Long): PersonEntity?

    /** 새 사람은 맨 뒤에 붙인다. 아무도 없으면 -1 */
    @Query("SELECT COALESCE(MAX(`order`), -1) FROM people")
    suspend fun maxOrder(): Int

    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Insert
    suspend fun insertAll(people: List<PersonEntity>)

    @Query("UPDATE people SET name = :name, dept = :dept, initial = :initial WHERE id = :id")
    suspend fun update(id: Long, name: String, dept: String, initial: String)

    @Query("UPDATE people SET `order` = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int)

    @Query("UPDATE people SET isHidden = :isHidden WHERE id = :id")
    suspend fun setHidden(id: Long, isHidden: Boolean)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM people")
    suspend fun clear()

    /** 첫 실행에서 등록한 사람으로 통째로 바꾼다 */
    @Transaction
    suspend fun replaceAll(people: List<PersonEntity>) {
        clear()
        insertAll(people)
    }
}
