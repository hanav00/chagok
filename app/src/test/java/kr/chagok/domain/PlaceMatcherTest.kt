package kr.chagok.domain

import kr.chagok.domain.model.TripPlace
import kr.chagok.testing.TripFixtures.CLIENT
import kr.chagok.testing.TripFixtures.HOME
import kr.chagok.testing.TripFixtures.OFFICE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 같은 장소 판단 [PlaceMatcher] */
class PlaceMatcherTest {
    private val baseLat = 37.3947
    private val baseLon = 127.1112
    private val saved = TripPlace(latitude = baseLat, longitude = baseLon)

    // 자오선을 따라 [meters] 만큼 북쪽 (하버사인에서 위도 차이만 있으면 거리 = 지구 반지름 × 각도)
    private fun north(meters: Double) =
        TripPlace(latitude = baseLat + Math.toDegrees(meters / EARTH_RADIUS_M), longitude = baseLon)

    @Test
    fun `둘 다 poiId 가 같으면 좌표가 멀어도 같은 곳`() {
        val a = TripPlace(poiId = "c-elec", latitude = 37.0, longitude = 127.0)
        val b = TripPlace(poiId = "c-elec", latitude = 37.5, longitude = 127.5)

        assertTrue(PlaceMatcher.isSamePlace(a, b))
    }

    @Test
    fun `poiId 가 같아도 poiSubId 가 다르면 다른 곳 (같은 건물 다른 회사)`() {
        val a = TripPlace(poiId = "tower", poiSubId = "3F", latitude = baseLat, longitude = baseLon)
        val b = TripPlace(poiId = "tower", poiSubId = "7F", latitude = baseLat, longitude = baseLon)

        assertFalse(PlaceMatcher.isSamePlace(a, b))
    }

    @Test
    fun `둘 다 poiId 가 있고 다르면 좌표가 같아도 다른 곳`() {
        val a = TripPlace(poiId = "c-elec", latitude = baseLat, longitude = baseLon)
        val b = TripPlace(poiId = "hotel", latitude = baseLat, longitude = baseLon)

        assertFalse(PlaceMatcher.isSamePlace(a, b))
    }

    @Test
    fun `한쪽만 poiId 가 있으면 좌표 반경으로 본다`() {
        val withPoi = TripPlace(poiId = "c-elec", latitude = baseLat, longitude = baseLon)

        assertTrue(PlaceMatcher.isSamePlace(withPoi, north(100.0)))
        assertFalse(PlaceMatcher.isSamePlace(withPoi, north(500.0)))
    }

    @Test
    fun `poiId 가 없으면 300m 안을 같은 곳으로 본다`() {
        val near = TripPlace(latitude = 37.3960, longitude = 127.1112) // 약 145m
        val far = TripPlace(latitude = 37.4000, longitude = 127.1112) // 약 590m

        assertTrue(PlaceMatcher.isSamePlace(saved, near))
        assertFalse(PlaceMatcher.isSamePlace(saved, far))
    }

    @Test
    fun `반경 경계 - 299·9m 는 같은 곳, 300·1m 는 다른 곳`() {
        val radius = ChagokConfig.PLACE_MATCH_RADIUS_M

        assertTrue(PlaceMatcher.isSamePlace(saved, north(radius - 0.1)))
        assertFalse(PlaceMatcher.isSamePlace(saved, north(radius + 0.1)))
    }

    @Test
    fun `반경은 경도 방향(동서)에도 같이 적용된다`() {
        val east = TripPlace(latitude = baseLat, longitude = baseLon + 0.002) // 위도 37° 에서 약 177m
        val farEast = TripPlace(latitude = baseLat, longitude = baseLon + 0.005) // 약 442m

        assertTrue(PlaceMatcher.isSamePlace(saved, east))
        assertFalse(PlaceMatcher.isSamePlace(saved, farEast))
    }

    @Test
    fun `이름이 같아도 poiId·좌표가 없으면 같은 곳으로 보지 않는다`() {
        val a = TripPlace(name = "회사", address = "판교역로 1")
        val b = TripPlace(name = "회사", address = "판교역로 1")

        assertFalse(PlaceMatcher.isSamePlace(a, b))
    }

    @Test
    fun `좌표가 한쪽이라도 없으면 다른 곳 (위도만 있거나 경도만 있어도)`() {
        assertFalse(PlaceMatcher.isSamePlace(saved, TripPlace()))
        assertFalse(PlaceMatcher.isSamePlace(saved, TripPlace(latitude = baseLat)))
        assertFalse(PlaceMatcher.isSamePlace(TripPlace(longitude = baseLon), saved))
    }

    @Test
    fun `장소가 null 이면 같은 곳이 아니다`() {
        assertFalse(PlaceMatcher.isSamePlace(null, saved))
        assertFalse(PlaceMatcher.isSamePlace(saved, null))
        assertFalse(PlaceMatcher.isSamePlace(null, null))
    }

    @Test
    fun `집에서 회사, 회사에서 집은 둘 다 집↔회사 운행`() {
        assertTrue(PlaceMatcher.isHomeOfficeTrip(HOME, OFFICE, HOME, OFFICE))
        assertTrue(PlaceMatcher.isHomeOfficeTrip(OFFICE, HOME, HOME, OFFICE))
    }

    @Test
    fun `집↔거래처, 회사↔회사는 집↔회사 운행이 아니다`() {
        assertFalse(PlaceMatcher.isHomeOfficeTrip(HOME, CLIENT, HOME, OFFICE))
        assertFalse(PlaceMatcher.isHomeOfficeTrip(CLIENT, OFFICE, HOME, OFFICE))
        assertFalse(PlaceMatcher.isHomeOfficeTrip(OFFICE, OFFICE, HOME, OFFICE))
    }

    @Test
    fun `집이나 회사를 정하지 않았으면 집↔회사 운행이 아니다`() {
        assertFalse(PlaceMatcher.isHomeOfficeTrip(HOME, OFFICE, home = null, office = OFFICE))
        assertFalse(PlaceMatcher.isHomeOfficeTrip(HOME, OFFICE, home = HOME, office = null))
    }

    private companion object {
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}
