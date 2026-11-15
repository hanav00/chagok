package kr.chagok.car

import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kr.chagok.domain.model.TripPlace

/**
 * Pleos NaviHelper 로 목적지·현재 위치·즐겨찾기를 받는다. 요청한 값은 리스너 이벤트로 돌아온다.
 * 목적지는 경로가 시작돼도 저절로 오지 않아 경로 시작·목적지 변경 이벤트를 받으면 직접 요청한다.
 */
class PleosNaviSource(context: Context) : NaviSource {
    private val navi = NaviHelper(context)
    private val _destination = MutableStateFlow<TripPlace?>(null)
    private val _bookmarks = MutableStateFlow(NaviBookmarks())
    private val locationUpdates =
        MutableSharedFlow<TripPlace>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val bookmarkUpdates =
        MutableSharedFlow<NaviBookmarks>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile
    private var isReady = false
    private var isStarted = false

    override val destination: StateFlow<TripPlace?> = _destination.asStateFlow()
    override val bookmarks: StateFlow<NaviBookmarks> = _bookmarks.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) = request("목적지") { navi.getDestinationInfo() }

        // 경로가 없으면 빈 목적지가 온다. 안내 중인 목적지가 없는 것(null)으로 둔다
        override fun onDestinationInfo(info: DestinationInfo) {
            _destination.value = TripPlace(
                name = info.name.orEmpty(),
                address = info.address.orEmpty(),
                poiId = info.poiId.orEmpty(),
                poiSubId = info.poiSubId.orEmpty(),
                latitude = info.latitude,
                longitude = info.longitude,
            ).takeUnless(TripPlace::isEmpty)
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            _destination.value = null
        }

        override fun onRouteCancelled() {
            _destination.value = null
        }

        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            locationUpdates.tryEmit(
                TripPlace(address = info.address.orEmpty(), latitude = info.latitude, longitude = info.longitude),
            )
        }

        override fun onBookmarkInfo(list: List<BookmarkInfo>) {
            val bookmarks = NaviBookmarks(
                home = list.firstOrNull { it.category == BookmarkType.HOME }?.toPlace(),
                office = list.firstOrNull { it.category == BookmarkType.OFFICE }?.toPlace(),
            )
            _bookmarks.value = bookmarks
            bookmarkUpdates.tryEmit(bookmarks)
        }

        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "내비 오류 $code")
        }

        // 안 물어 보면 처음 목적지로 도착지·출퇴근을 판단한다
        override fun onDestinationChanged() = request("바뀐 목적지") { navi.getDestinationInfo() }

        override fun onNavigationStatus(status: NavigationStatus) = Unit
        override fun onRouteStateInfo(info: RouteStateInfo) = Unit
    }

    override fun start() {
        if (isStarted) return
        isStarted = true
        navi.initialize(
            {
                isReady = true
                navi.addListener(listener)
                request("즐겨찾기") { navi.getBookmarkInfo() }
                request("목적지") { navi.getDestinationInfo() } // 앱이 켜지기 전에 시작한 경로
                request("현재 위치") { navi.getCurrentLocationInfo() }
            },
            { error -> Log.e(TAG, "NaviHelper 연결 실패", error) },
        )
    }

    override fun stop() {
        if (!isStarted) return
        isStarted = false
        isReady = false
        runCatching { navi.removeListener(listener) }
        navi.release()
    }

    override suspend fun currentLocation(): TripPlace? {
        val last = locationUpdates.replayCache.lastOrNull()
        return requestLocation(CarConfig.LOCATION_TIMEOUT_MILLIS) ?: last
    }

    override suspend fun freshLocation(): TripPlace? = requestLocation(CarConfig.FRESH_LOCATION_TIMEOUT_MILLIS)

    // 지난 답을 지우고 이번 요청의 답을 기다린다 (requestLocation 과 같은 방식)
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun freshBookmarks(): NaviBookmarks {
        bookmarkUpdates.resetReplayCache()
        request("즐겨찾기") { navi.getBookmarkInfo() }
        return withTimeoutOrNull(CarConfig.BOOKMARKS_TIMEOUT_MILLIS) { bookmarkUpdates.first() }
            ?: _bookmarks.value.also { Log.w(TAG, "즐겨찾기 응답이 오지 않아 가지고 있던 값을 씀") }
    }

    // resetReplayCache 는 아직 실험 API 다. 지난 위치를 지워야 first() 가 이번 요청의 답을 기다린다
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun requestLocation(timeoutMillis: Long): TripPlace? {
        locationUpdates.resetReplayCache()
        request("현재 위치") { navi.getCurrentLocationInfo() }
        return withTimeoutOrNull(timeoutMillis) { locationUpdates.first() }
            .also { if (it == null) Log.w(TAG, "현재 위치 응답이 ${timeoutMillis}ms 안에 오지 않음") }
    }

    private fun request(what: String, call: () -> Unit) {
        if (!isReady) return
        runCatching(call).onFailure { Log.w(TAG, "$what 요청 실패", it) }
    }

    private fun BookmarkInfo.toPlace() = TripPlace(
        name = name.orEmpty(),
        address = address.orEmpty(),
        poiId = poiId.orEmpty(),
        poiSubId = poiSubId.orEmpty(),
        latitude = latitude,
        longitude = longitude,
    )

    private companion object {
        const val TAG = "PleosNaviSource"
    }
}
