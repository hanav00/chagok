package kr.chagok.data.db

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.TripPlace
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.inMemoryDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 운행 한 건이 Room 표에 들어갔다 나와도 그대로인지 (Trip → TripEntity → DB → Trip) */
@RunWith(RobolectricTestRunner::class)
class TripEntityTest {
    private lateinit var db: ChagokDatabase
    private lateinit var dao: TripDao

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        dao = db.tripDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun roundTrip(trip: Trip): Trip {
        val id = dao.insert(TripEntity.from(trip))
        return requireNotNull(dao.find(id)).toDomain()
    }

    @Test
    fun `장소의 모든 칸과 비고가 있는 업무 운행은 DB 를 거쳐도 그대로다`() = runBlocking {
        val trip = TripFixtures.trip(
            origin = TripPlace(
                "회사", "성남시 분당구 판교역로 1", poiId = "office", poiSubId = "2", latitude = 37.401, longitude = 127.108,
            ),
            destination = TripPlace(
                "○○전자", "수원시 영통구 1", poiId = "client", poiSubId = "", latitude = 37.395, longitude = 127.111,
            ),
            note = "납품 미팅",
            category = Category.COMMUTE,
            reason = ClassifyReason.PRE_PICK,
        )

        val saved = roundTrip(trip)

        assertEquals(trip.copy(id = saved.id), saved)
    }

    @Test
    fun `장소가 없는 개인 운행은 장소가 빈 값(null)으로 돌아온다`() = runBlocking {
        val trip = TripFixtures.trip(category = Category.PERSONAL)

        val saved = roundTrip(trip)

        assertEquals(trip.copy(id = saved.id), saved)
        assertNull(saved.origin)
        assertNull(saved.destination)
    }

    @Test
    fun `좌표 없이 주소만 아는 장소(내비 없이 운행)도 좌표 없이 돌아온다`() = runBlocking {
        val trip = TripFixtures.trip(origin = null, destination = TripPlace(address = "성남시 수정구 어딘가 3"))

        val saved = roundTrip(trip)

        assertEquals(trip.copy(id = saved.id), saved)
        assertNull(saved.destination!!.latitude)
    }

    @Test
    fun `분류 대기인 기록 안 된 주행은 시각·사람·분류·근거가 빈 채로 돌아온다`() = runBlocking {
        val gap = TripFixtures.trip(category = null, personId = null, isGap = true, origin = null, destination = null)

        val saved = roundTrip(gap)

        assertEquals(gap.copy(id = saved.id), saved)
    }

    @Test
    fun `분류와 근거는 이름(글자)으로 저장된다 (열거형 순서를 바꿔도 기록이 바뀌지 않는다)`() = runBlocking {
        val id = dao.insert(
            TripEntity.from(TripFixtures.trip(category = Category.PERSONAL, reason = ClassifyReason.USER_EDIT)),
        )

        val (category, reason) = db.query("SELECT category, reason FROM trips WHERE id = ?", arrayOf(id)).use {
            it.moveToFirst()
            it.getString(0) to it.getString(1)
        }

        assertEquals("PERSONAL", category)
        assertEquals("USER_EDIT", reason)
    }
}
