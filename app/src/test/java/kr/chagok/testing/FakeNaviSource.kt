package kr.chagok.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kr.chagok.car.NaviBookmarks
import kr.chagok.car.NaviSource
import kr.chagok.domain.model.TripPlace

/**
 * 가짜 내비. 목적지·지금 위치·즐겨찾기를 테스트가 정한다. 위치를 몇 번 물었는지 센다 (개인 운행은 묻지 않아야 한다).
 * [isLate] 면 위치 요청에 답이 안 온다: [currentLocation] 은 마지막으로 답한 위치, [freshLocation] 은 null (실제 내비와 같다)
 */
class FakeNaviSource(
    var location: TripPlace? = null,
    bookmarks: NaviBookmarks = NaviBookmarks(),
) : NaviSource {
    override val destination = MutableStateFlow<TripPlace?>(null)
    override val bookmarks = MutableStateFlow(bookmarks)
    var locationRequests = 0
        private set
    var isLate = false

    /** 주차한 자리 위치를 답하기 직전에 돈다 (위치를 기다리는 동안 화면에서 누른 것을 흉내 낼 때) */
    var whileLocating: suspend () -> Unit = {}
    private var lastAnswered: TripPlace? = null

    override fun start() = Unit

    override fun stop() = Unit

    override suspend fun currentLocation(): TripPlace? = freshLocation() ?: lastAnswered

    override suspend fun freshBookmarks(): NaviBookmarks = bookmarks.value

    override suspend fun freshLocation(): TripPlace? {
        locationRequests++
        whileLocating()
        if (isLate) return null
        return location.also { lastAnswered = it }
    }
}
