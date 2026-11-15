package kr.chagok

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kr.chagok.car.NaviSource
import kr.chagok.car.PleosNaviSource
import kr.chagok.car.PleosVehicleSource
import kr.chagok.car.PleosVoiceInput
import kr.chagok.car.PleosVoiceOutput
import kr.chagok.car.VehicleSource
import kr.chagok.car.VoiceInput
import kr.chagok.car.VoiceOutput
import kr.chagok.data.adminStore
import kr.chagok.data.db.ChagokDatabase
import kr.chagok.data.settingsStore
import kr.chagok.gleo.GleoProtocol
import java.time.Clock

/**
 * 앱 바깥과 닿는 것: 저장 파일, 차(Pleos SDK), 시계, 코루틴 범위.
 * 실제 앱은 [real] 을 쓰고, 테스트는 메모리 DB·가짜 차를 넣어 [AppContainer] 를 그대로 만든다.
 */
class AppEnvironment(
    val database: ChagokDatabase,
    val settingsStore: DataStore<Preferences>,
    val adminStore: DataStore<Preferences>,
    val vehicle: VehicleSource,
    val navi: NaviSource,
    val voiceOutput: VoiceOutput,
    /** 음성 인식(STT)은 마이크 버튼을 처음 누를 때 연결한다 */
    val voiceInput: () -> VoiceInput,
    val clock: Clock,
    val scope: CoroutineScope,
    /** Gleo AI 가 깔린 차인지. 없으면 첫 실행이 집·회사 다음 바로 정리 화면으로 간다 */
    val isGleoAvailable: Boolean = false,
) {
    companion object {
        private const val TAG = "AppEnvironment"

        // 앱 범위에서 모으는 흐름(Room·DataStore)이 예상 못 한 예외를 내도 앱이 죽지 않게 로그만 남긴다.
        // 쓰기 실패는 각 저장소가 호출부로 넘기므로 여기까지 오지 않는다
        private val loggingHandler = CoroutineExceptionHandler { _, error -> Log.e(TAG, "앱 범위 작업 실패", error) }

        fun real(context: Context): AppEnvironment {
            val appContext = context.applicationContext
            return AppEnvironment(
                database = ChagokDatabase.open(appContext),
                settingsStore = appContext.settingsStore,
                adminStore = appContext.adminStore,
                vehicle = PleosVehicleSource(appContext),
                navi = PleosNaviSource(appContext),
                voiceOutput = PleosVoiceOutput(appContext),
                voiceInput = { PleosVoiceInput(appContext) },
                clock = Clock.systemDefaultZone(),
                scope = CoroutineScope(SupervisorJob() + loggingHandler),
                isGleoAvailable = isInstalled(appContext, GleoProtocol.GLEO_PACKAGE),
            )
        }

        private fun isInstalled(context: Context, packageName: String): Boolean =
            runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
    }
}
