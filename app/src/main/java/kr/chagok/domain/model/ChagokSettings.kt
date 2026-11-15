package kr.chagok.domain.model

import kr.chagok.domain.ChagokConfig
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 사용자가 정한 기록 방식과 등록 정보. 첫 실행에서 고르고 설정에서 바꾼다.
 *
 * @property installedOn 앱을 처음 연 날. 사용법 안내를 처음 며칠만 보여 주는 데 쓴다
 * @property installUid 이 앱 설치의 식별값 ([BundleHeader]). 차대번호를 못 읽은 차의 묶음이 어느 설치에서 나왔는지 가린다
 * @property home 집 (선택). 집·회사가 모두 있어야 업무 운행을 출퇴근으로 나눈다
 * @property isChangeTried 운행 분류를 말(Gleo)이나 연습 1탭으로 바꿔 본 적이 있다 (홈 시작 가이드 "말해 보기")
 */
data class ChagokSettings(
    val isFirstRunDone: Boolean = false,
    val defaultCategory: Category = Category.BUSINESS,
    val usage: VehicleUsage = VehicleUsage.SOLO,
    val isTripVoiceOn: Boolean = true,
    val installedOn: LocalDate? = null,
    val installUid: String = "",
    val home: TripPlace? = null,
    val office: TripPlace? = null,
    val vehicle: VehicleInfo = VehicleInfo(),
    val isChangeTried: Boolean = false,
    val isGuideDismissed: Boolean = false,
) {
    val hasHomeOffice: Boolean get() = home != null && office != null

    /**
     * 사용법 안내를 보여 주는 기간: 설치한 날이 1일째, [ChagokConfig.HINT_DAYS] 일째까지.
     * 설치일을 아직 못 남겼으면 막 설치한 것으로 본다. 지금 상태를 알리는 문구는 기간과 상관없이 보인다
     */
    fun isHintPeriod(today: LocalDate): Boolean {
        val installed = installedOn ?: return true
        return ChronoUnit.DAYS.between(installed, today) + 1 <= ChagokConfig.HINT_DAYS
    }
}

/**
 * 서식 머리에 들어가는 차량·사업자 정보. SDK 로 읽는 건 차대번호뿐이고 나머지는 직접 넣는다.
 * TODO: 차종을 차대번호로 추정해 채워 두고 사용자가 확인하게 한다
 */
data class VehicleInfo(
    val vin: String = "",
    val model: String = "",
    val plate: String = "",
    val company: String = "",
    val bizNo: String = "",
)
