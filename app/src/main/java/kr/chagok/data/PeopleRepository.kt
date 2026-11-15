package kr.chagok.data

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kr.chagok.data.db.ChagokDatabase
import kr.chagok.data.db.PersonEntity
import kr.chagok.domain.NewPerson
import kr.chagok.domain.model.Person

/**
 * 이 차를 쓰는 사람 (서식 ④). 첫 실행에서 등록하고, 설정에서 관리자가 더하고·고치고·순서를 바꾸고·지운다.
 * 숨기지 않은 첫 사람이 기본 사용자다. 지난 운행에 쓰인 사람은 지우지 않고 숨긴다 (기록부 이름이 남아야 한다).
 */
class PeopleRepository(private val db: ChagokDatabase, scope: CoroutineScope) {
    private val personDao = db.personDao()

    /** 숨긴 사람까지 등록 순서대로. 지난 운행·수정 기록의 이름을 찾을 때 */
    val allPeople: StateFlow<List<Person>> = personDao.observeAll()
        .map { list -> list.map(PersonEntity::toDomain) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** 지금 쓰는 사람 (숨긴 사람 빼고). 고르는 목록·기본 사용자 */
    val people: StateFlow<List<Person>> = allPeople
        .map { list -> list.filterNot(Person::isHidden) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** 숨긴 사람까지 찾는다 (지난 운행의 사람) */
    fun find(id: Long?): Person? = id?.let { allPeople.value.firstOrNull { person -> person.id == it } }

    suspend fun replaceAll(people: List<NewPerson>) {
        val entities = people.mapIndexed { index, person ->
            PersonEntity(name = person.name.trim(), dept = person.dept.trim(), initial = person.initial, order = index)
        }
        write("사람 등록") { personDao.replaceAll(entities) }
    }

    /** 맨 뒤에 한 사람 더한다. 이름이 없으면 얼굴 원은 번호 ("이름 없음 3" → "3") */
    suspend fun add(name: String, dept: String): Long = write("사람 추가") {
        db.withTransaction {
            val order = personDao.maxOrder() + 1
            val initial = Person.initialOf(name, unnamed = (order + 1).toString())
            personDao.insert(PersonEntity(name = name.trim(), dept = dept.trim(), initial = initial, order = order))
        }
    }

    /** 이름·부서를 고친다. 이름을 비우면 얼굴 원은 등록 번호로 돌아간다 */
    suspend fun update(id: Long, name: String, dept: String) = write("사람 고치기") {
        db.withTransaction {
            val saved = personDao.get(id) ?: return@withTransaction
            // 원래 이름 없던 사람은 얼굴 원("나"·번호)을 그대로 둔다
            val unnamed = if (saved.name.isBlank()) saved.initial else (saved.order + 1).toString()
            personDao.update(id, name.trim(), dept.trim(), Person.initialOf(name, unnamed))
        }
    }

    /**
     * 한 칸 위로: 바로 위의 (숨기지 않은) 사람과 자리를 바꾼다. 맨 위가 기본 사용자다.
     * 숨긴 사람은 건너뛴다 (보이는 순서만 바뀐다). 이미 맨 위면 그대로.
     */
    suspend fun moveUp(id: Long) = write("사람 순서 바꾸기") {
        db.withTransaction {
            val visible = personDao.getAll().filterNot(PersonEntity::isHidden)
            val index = visible.indexOfFirst { it.id == id }
            if (index <= 0) return@withTransaction
            val above = visible[index - 1]
            val person = visible[index]
            personDao.setOrder(person.id, above.order)
            personDao.setOrder(above.id, person.order)
        }
    }

    /**
     * 이 사람이 지난 운행·진행 중 운행·관리자 수정 기록에 나오는지. 나오면 지우지 않고 숨긴다 ([remove]).
     * 진행 중 운행도 본다: 앱이 다시 켜져 마감을 미루는 동안 지우면, 마감된 운행이 없는 사람을 가리킨다
     */
    suspend fun isUsed(id: Long): Boolean = read("사람 쓰임 확인", fallback = true) {
        db.tripDao().countOfPerson(id) > 0 ||
            db.activeTripDao().countOfPerson(id) > 0 ||
            db.adminEditDao().countOfPerson(id.toString()) > 0
    }

    /** 지운다. 지난 기록에 나오는 사람은 숨기기만 한다 (기록부 ④성명이 비면 안 된다) */
    suspend fun remove(id: Long): RemoveResult = write("사람 지우기") {
        db.withTransaction {
            if (isUsed(id)) {
                personDao.setHidden(id, isHidden = true)
                RemoveResult.HIDDEN
            } else {
                personDao.delete(id)
                RemoveResult.DELETED
            }
        }
    }

    /** 숨긴 사람을 다시 고르는 목록에 */
    suspend fun unhide(id: Long) = write("사람 다시 보이기") { personDao.setHidden(id, isHidden = false) }

    private inline fun <T> write(what: String, block: () -> T): T = writeOrThrow(TAG, what, block)

    // 쓰임을 못 읽으면 지우지 않는 쪽(숨기기)으로
    private inline fun <T> read(what: String, fallback: T, block: () -> T): T =
        readOrDefault(TAG, what, fallback, block)

    private companion object {
        const val TAG = "PeopleRepository"
    }
}

/** 사람 지우기 결과: 지웠다 · 지난 기록에 나와 숨겼다 */
enum class RemoveResult { DELETED, HIDDEN }
