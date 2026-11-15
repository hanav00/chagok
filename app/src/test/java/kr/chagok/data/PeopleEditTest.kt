package kr.chagok.data

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.TripFixtures.trip
import kr.chagok.testing.seedTrips
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 설정에서 관리자가 사람을 더하고·고치고·지운다.
 * 지난 운행에 쓰인 사람은 지우지 않고 숨긴다. 기록부 이름은 남고, 고르는 목록·기본 사용자에서만 빠진다.
 */
@RunWith(RobolectricTestRunner::class)
class PeopleEditTest {
    private lateinit var app: TestApp

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김지은"))
    }

    @After
    fun tearDown() = app.close()

    private suspend fun awaitPeople(condition: (List<Person>) -> Boolean) = await(app.people.people, condition)

    @Test
    fun `사람을 더하면 맨 뒤에 붙고 얼굴 원은 이름 첫 글자다`() = runBlocking<Unit> {
        app.people.add(" 이도윤 ", "영업팀")

        val people = awaitPeople { it.size == 3 }
        assertEquals(listOf("박정훈", "김지은", "이도윤"), people.map { it.name })
        assertEquals("영업팀", people[2].dept)
        assertEquals("이", people[2].initial)
    }

    @Test
    fun `이름 없이 더하면 빈 이름으로 두고 얼굴 원은 등록 번호다`() = runBlocking<Unit> {
        app.people.add("", "")

        val added = awaitPeople { it.size == 3 }.last()
        assertEquals("", added.name)
        assertFalse(added.isNamed)
        assertEquals("3", added.initial)
    }

    @Test
    fun `이름을 고치면 얼굴 원 글자도 바뀌고, 이름을 비우면 등록 번호로 돌아간다`() = runBlocking<Unit> {
        val kim = app.people.people.value[1]

        app.people.update(kim.id, "김지은", "총무팀")
        assertEquals("총무팀", awaitPeople { it[1].dept == "총무팀" }[1].dept)

        app.people.update(kim.id, "최지은", "총무팀")
        assertEquals("최", awaitPeople { it[1].name == "최지은" }[1].initial)

        app.people.update(kim.id, " ", "")
        val cleared = awaitPeople { !it[1].isNamed }[1]
        assertEquals("2", cleared.initial)
    }

    @Test
    fun `이름 없는 사람의 부서만 고치면 얼굴 원 번호는 그대로다`() = runBlocking<Unit> {
        val me = app.people.people.value[0]
        app.people.update(me.id, "", "")
        awaitPeople { !it[0].isNamed }

        app.people.update(me.id, "", "영업팀")

        val again = awaitPeople { it[0].dept == "영업팀" }[0]
        assertEquals("이름 없던 사람은 얼굴 원을 그대로 둔다", "1", again.initial)
    }

    @Test
    fun `운행에 안 쓰인 사람은 지우면 정말 지워진다`() = runBlocking<Unit> {
        val kim = app.people.people.value[1]

        assertEquals(RemoveResult.DELETED, app.people.remove(kim.id))

        await(app.people.allPeople) { list -> list.none { it.id == kim.id } }
        assertNull(app.people.find(kim.id))
    }

    @Test
    fun `지난 운행에 쓰인 사람은 지우지 않고 숨긴다 - 기록부에는 남고 고르는 목록에서만 빠진다`() = runBlocking<Unit> {
        val kim = app.people.people.value[1]
        app.nextDeparture.pickPerson(kim.id)
        await(app.nextDeparture.next) { it?.personId == kim.id }
        val trip = app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        assertEquals(RemoveResult.HIDDEN, app.people.remove(kim.id))

        val visible = awaitPeople { it.size == 1 }
        assertEquals(listOf("박정훈"), visible.map { it.name })
        assertEquals("김지은", app.people.find(trip.personId)!!.name)
        assertTrue(app.people.find(trip.personId)!!.isHidden)
    }

    @Test
    fun `달리는 중인 운행에 탄 사람은 지우지 않고 숨긴다 (마감된 운행이 없는 사람을 가리키지 않게)`() = runBlocking<Unit> {
        val kim = app.people.people.value[1]
        app.nextDeparture.pickPerson(kim.id)
        await(app.nextDeparture.next) { it?.personId == kim.id }
        app.startDriving()

        assertEquals(RemoveResult.HIDDEN, app.people.remove(kim.id))
        assertEquals("김지은", app.people.find(kim.id)!!.name)
    }

    @Test
    fun `숨긴 사람을 다시 보이면 고르는 목록에 돌아온다`() = runBlocking<Unit> {
        val kim = app.people.people.value[1]
        app.nextDeparture.pickPerson(kim.id)
        app.driveTrip(km = 3.0)
        app.people.remove(kim.id)
        awaitPeople { it.size == 1 }

        app.people.unhide(kim.id)

        assertEquals(listOf("박정훈", "김지은"), awaitPeople { it.size == 2 }.map { it.name })
    }

    @Test
    fun `누가 타나요에서 고른 사람을 숨기면 다음 출발은 미지정으로 돌아간다`() = runBlocking<Unit> {
        val kim = app.people.people.value[1]
        app.nextDeparture.pickPerson(kim.id)
        app.driveTrip(km = 3.0) // 쓰인 사람이 되게
        app.nextDeparture.pickPerson(kim.id)
        await(app.nextDeparture.next) { it?.personId == kim.id }

        app.people.remove(kim.id)

        await(app.nextDeparture.next) { it?.personId == null }
    }

    @Test
    fun `혼자 쓰는 차에서 첫 사람을 숨기면 다음 사람이 기본 사용자가 된다`() = runBlocking<Unit> {
        app.settings.setUsage(VehicleUsage.SOLO)
        val (park, kim) = app.people.people.value
        await(app.nextDeparture.next) { it?.personId == park.id }
        app.driveTrip(km = 3.0)

        assertEquals(RemoveResult.HIDDEN, app.people.remove(park.id))

        await(app.nextDeparture.next) { it?.personId == kim.id }
    }

    @Test
    fun `위로 → 바로 위 사람과 자리를 바꾼다`() = runBlocking<Unit> {
        app.people.add("이서연", "")
        val lee = awaitPeople { it.size == 3 }[2]

        app.people.moveUp(lee.id)

        assertEquals(listOf("박정훈", "이서연", "김지은"), awaitPeople { it[1].id == lee.id }.map { it.name })
    }

    @Test
    fun `맨 위 사람을 위로 → 그대로`() = runBlocking<Unit> {
        val park = app.people.people.value[0]

        app.people.moveUp(park.id)
        app.people.moveUp(999) // 모르는 사람도 그대로

        assertEquals(listOf("박정훈", "김지은"), app.people.people.value.map { it.name })
    }

    @Test
    fun `숨긴 사람은 건너뛰고 보이는 바로 위 사람과 바꾼다`() = runBlocking<Unit> {
        app.people.add("이서연", "")
        val (_, kim, lee) = awaitPeople { it.size == 3 }
        app.seedTrips(trip(personId = kim.id))
        app.people.remove(kim.id) // 지난 운행에 나와서 숨겨진다
        awaitPeople { it.size == 2 }

        app.people.moveUp(lee.id)

        assertEquals(listOf("이서연", "박정훈"), awaitPeople { it[0].id == lee.id }.map { it.name })
        assertEquals("숨긴 사람은 그대로 남는다", 3, app.people.allPeople.value.size)
    }

    @Test
    fun `혼자 쓰는 차에서 둘째 사람을 맨 위로 → 그 사람이 기본 사용자가 된다`() = runBlocking<Unit> {
        app.settings.setUsage(VehicleUsage.SOLO)
        val (park, kim) = app.people.people.value
        await(app.nextDeparture.next) { it?.personId == park.id }

        app.people.moveUp(kim.id)

        await(app.nextDeparture.next) { it?.personId == kim.id }
    }
}
