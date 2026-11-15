package kr.chagok.data.db

import kr.chagok.domain.model.TripPlace

/** 표 안에 펼쳐 저장하는 장소 한 점 (@Embedded, 접두사로 출발지·도착지를 나눈다) */
data class PlaceColumns(
    val name: String,
    val address: String,
    val poiId: String,
    val poiSubId: String,
    val latitude: Double?,
    val longitude: Double?,
) {
    fun toDomain(): TripPlace = TripPlace(name, address, poiId, poiSubId, latitude, longitude)

    companion object {
        fun from(place: TripPlace?): PlaceColumns? = place?.run {
            PlaceColumns(name, address, poiId, poiSubId, latitude, longitude)
        }
    }
}
