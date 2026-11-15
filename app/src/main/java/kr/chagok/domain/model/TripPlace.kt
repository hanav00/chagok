package kr.chagok.domain.model

/**
 * 장소 한 점. 운행의 출발지·도착지(업무 운행만), 집·회사 등록에 쓴다.
 * 내비 목적지면 name·poiId 가 있고, 내비 없이 운행했으면 그 자리 주소(address)만 있다.
 * 이동 경로는 저장하지 않는다. 거리는 계기판으로 잰다.
 */
data class TripPlace(
    val name: String = "",
    val address: String = "",
    val poiId: String = "",
    val poiSubId: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** 내비 목적지·즐겨찾기처럼 이름이 있는 곳인지 (없으면 주소만 아는 곳) */
    val hasName: Boolean get() = name.isNotBlank()

    /**
     * 이름·주소·poiId 가 다 비었는지. 경로가 없을 때 내비는 빈 목적지(좌표 0,0)를 주는데,
     * 이것을 목적지로 붙이면 주차한 자리 주소를 도착지로 남기지 못한다.
     */
    val isEmpty: Boolean get() = name.isBlank() && address.isBlank() && poiId.isBlank()

    /** 화면에 보일 이름: 이름이 없으면 주소 */
    val displayName: String get() = name.ifBlank { address }
}
