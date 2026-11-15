package kr.chagok.car

import kotlinx.coroutines.flow.StateFlow
import kr.chagok.domain.model.TripPlace

/**
 * 내비에서 받는 값: 안내 중인 목적지, 지금 위치, 즐겨찾기 집·회사.
 * 요청(get*Info)은 값을 바로 돌려주지 않고 이벤트로 보내 준다.
 */
interface NaviSource {
    /** 안내 중인 목적지. 도착하거나 경로를 취소하면 null */
    val destination: StateFlow<TripPlace?>

    /** 내비 즐겨찾기의 집·회사 (에뮬레이터는 비어 있다) */
    val bookmarks: StateFlow<NaviBookmarks>

    fun start()

    fun stop()

    /**
     * 지금 서 있는 곳 (주소·좌표, 이름 없음). 응답이 늦으면 마지막으로 받은 위치.
     * 출발 순간·첫 실행 "지금 여기"처럼 차가 서 있던 자리에 쓴다. 마지막 위치도 대개 그 자리다
     */
    suspend fun currentLocation(): TripPlace?

    /**
     * 이번 요청의 답으로만 받은 지금 위치. 늦으면 null.
     * 주차한 자리에 쓴다. 마지막 위치는 대개 출발 때 읽은 것이라, 대신 쓰면 도착지가 출발지 주소로 남는다
     */
    suspend fun freshLocation(): TripPlace?

    /**
     * 내비 즐겨찾기를 지금 다시 읽는다. [bookmarks] 는 앱이 켜질 때 한 번만 읽어 그 뒤 내비에서 바꾼 집·회사가
     * 반영되지 않는다. 답이 늦으면 가지고 있던 값
     */
    suspend fun freshBookmarks(): NaviBookmarks
}

data class NaviBookmarks(val home: TripPlace? = null, val office: TripPlace? = null)
