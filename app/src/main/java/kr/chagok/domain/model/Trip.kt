package kr.chagok.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * 운행 한 건 = 국세청 운행기록부 한 줄. 거리는 계기판 누적거리(서식 ⑤⑥)로만 잰다.
 * 장소는 업무 운행에만 남기고, 기록 안 된 주행([isGap])은 앱이 꺼진 사이 계기판이 늘어난 구간이라 시각·장소·사람을 모른다.
 *
 * @property uid 차 밖에서도 유일한 식별값 ([RecordUid]). 만들 때 정하고 고칠 때는 그대로 둔다
 * @property personId null 이면 사용자 미지정(공용차에서 안 고르고 출발) 또는 기록 안 된 주행
 * @property category null 이면 기록 안 된 주행을 아직 분류하지 않은 것
 */
data class Trip(
    val id: Long = 0,
    val uid: String = RecordUid.new(),
    val date: LocalDate,
    val personId: Long?,
    val origin: TripPlace?,
    val destination: TripPlace?,
    val startKm: Double,
    val endKm: Double,
    val startedAt: LocalTime?,
    val endedAt: LocalTime?,
    val category: Category?,
    val reason: ClassifyReason?,
    val note: String = "",
    val isGap: Boolean = false,
) {
    /** ⑦주행거리 */
    val km: Double get() = endKm - startKm

    val isPersonal: Boolean get() = category == Category.PERSONAL

    /** 기록 안 된 주행을 아직 고르지 않음 */
    val isPending: Boolean get() = category == null

    /** 공용차에서 누가 탔는지 안 고른 운행. 기록 안 된 주행은 시각도 몰라 사람을 묻지 않는다 */
    val isUnassigned: Boolean get() = !isGap && personId == null

    /** 국세청 별지 서식 한 줄의 거리 칸. ⑧출·퇴근 ⑨일반 업무는 해당 분류일 때만 값이 있다 */
    fun formDistances(): FormDistances = FormDistances(
        startKm = startKm,
        endKm = endKm,
        km = km,
        commuteKm = if (category == Category.COMMUTE) km else 0.0,
        businessKm = if (category == Category.BUSINESS) km else 0.0,
    )

    /** 분류를 바꾼 운행. 개인이 되면 장소를 지운다 (개인 운행은 위치를 남기지 않는다) */
    fun reclassified(newCategory: Category, newReason: ClassifyReason): Trip {
        val isNowPersonal = newCategory == Category.PERSONAL
        return copy(
            category = newCategory,
            reason = newReason,
            origin = if (isNowPersonal) null else origin,
            destination = if (isNowPersonal) null else destination,
        )
    }
}

/** 서식 ⑤~⑨ 칸 값 (km) */
data class FormDistances(
    val startKm: Double,
    val endKm: Double,
    val km: Double,
    val commuteKm: Double,
    val businessKm: Double,
)
