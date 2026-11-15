package kr.chagok.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kr.chagok.data.db.ChagokDatabase
import kr.chagok.domain.NewPerson
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.NextDeparture
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.inMemoryDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 이 차를 쓰는 사람 (서식 ④사용자). 첫 실행에서 등록한 순서가 곧 보이는 순서이고, 첫 사람이 기본 사용자다 */
@RunWith(RobolectricTestRunner::class)
class PeopleRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var db: ChagokDatabase
    private lateinit var repo: PeopleRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repo = PeopleRepository(db, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private val team = listOf(
        NewPerson("최서연", "최", dept = "영업팀"),
        NewPerson("김민수", "김"),
        NewPerson("박정훈", "박", dept = "대표"),
    )

    @Test
    fun `처음에는 아무도 없다`() {
        assertEquals(emptyList<Any>(), repo.people.value)
    }

    @Test
    fun `등록한 순서대로 보이고 이름·부서·얼굴 글자가 그대로 남는다 (가나다순이 아니다)`() = runBlocking {
        repo.replaceAll(team)

        val people = await(repo.people) { it.size == 3 }
        assertEquals(listOf("최서연", "김민수", "박정훈"), people.map { it.name })
        assertEquals(listOf("영업팀", "", "대표"), people.map { it.dept })
        assertEquals(listOf("최", "김", "박"), people.map { it.initial })
    }

    @Test
    fun `첫 사람이 기본 사용자라 혼자 쓰는 차의 다음 출발은 첫 사람으로 정해진다`() = runBlocking {
        repo.replaceAll(team)
        val people = await(repo.people) { it.size == 3 }

        val next = NextDeparture.resolve(
            Category.BUSINESS,
            VehicleUsage.SOLO,
            people,
            pickedCategory = null,
            pickedPersonId = null,
        )

        assertEquals("최서연", repo.find(next.personId)!!.name)
    }

    @Test
    fun `다시 등록하면 앞 명단을 통째로 바꾸고 사람이 겹치지 않는다`() = runBlocking {
        repo.replaceAll(team)
        await(repo.people) { it.size == 3 }

        repo.replaceAll(listOf(NewPerson("박정훈", "박"), NewPerson("이도윤", "이")))

        val people = await(repo.people) { it.size == 2 }
        assertEquals(listOf("박정훈", "이도윤"), people.map { it.name })
        assertEquals("id 가 겹치지 않는다", 2, people.map { it.id }.toSet().size)
    }

    @Test
    fun `id 로 사람을 찾고, id 가 없거나 모르는 id 면 null`() = runBlocking {
        repo.replaceAll(team)
        val people = await(repo.people) { it.size == 3 }

        assertEquals("김민수", repo.find(people[1].id)!!.name)
        assertNull(repo.find(null))
        assertNull(repo.find(999))
    }
}
