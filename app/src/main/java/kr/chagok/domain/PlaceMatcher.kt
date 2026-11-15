package kr.chagok.domain

import kr.chagok.domain.model.TripPlace
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 두 장소가 같은 곳인지. 둘 다 내비 poiId 가 있으면 poiId·poiSubId 로 맞춰 같은 건물의 다른 회사와 헷갈리지 않게 하고,
 * 아니면 좌표 반경([ChagokConfig.PLACE_MATCH_RADIUS_M])으로 본다.
 */
object PlaceMatcher {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun isSamePlace(a: TripPlace?, b: TripPlace?): Boolean {
        if (a == null || b == null) return false
        if (a.poiId.isNotEmpty() && b.poiId.isNotEmpty()) return a.poiId == b.poiId && a.poiSubId == b.poiSubId
        val meters = distanceMeters(a, b) ?: return false
        return meters <= ChagokConfig.PLACE_MATCH_RADIUS_M
    }

    /** 집 ↔ 회사 운행인지 (어느 방향이든) */
    fun isHomeOfficeTrip(origin: TripPlace?, destination: TripPlace?, home: TripPlace?, office: TripPlace?): Boolean =
        (isSamePlace(origin, home) && isSamePlace(destination, office)) ||
            (isSamePlace(origin, office) && isSamePlace(destination, home))

    // 하버사인 공식. 좌표가 하나라도 없으면 null
    private fun distanceMeters(a: TripPlace, b: TripPlace): Double? {
        val lat1 = a.latitude ?: return null
        val lon1 = a.longitude ?: return null
        val lat2 = b.latitude ?: return null
        val lon2 = b.longitude ?: return null
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }
}
