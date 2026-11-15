package kr.chagok.domain

import kr.chagok.domain.model.Category
import kr.chagok.domain.model.TripPlace
import kr.chagok.domain.model.VehicleUsage

/**
 * 첫 실행 화면에서 고른 값. 필수 입력은 없고, "나중에"로 건너뛰면 기본 분류 업무 · 혼자 쓰는 차 · 집·회사 없음이다.
 *
 * @property people 등록한 사람 (첫 사람 = 이 화면을 쓰는 분). 혼자 쓰는 차는 첫 사람만 남는다
 */
data class FirstRunChoice(
    val isSkipped: Boolean,
    val usage: VehicleUsage?,
    val defaultCategory: Category,
    val home: TripPlace?,
    val office: TripPlace?,
    val people: List<NewPerson>,
) {
    /** 실제로 적용될 값. 첫 실행 정리 화면도 이 결과를 그대로 보여 준다 */
    fun toResult(): FirstRunResult {
        if (isSkipped) {
            return FirstRunResult(VehicleUsage.SOLO, Category.BUSINESS, home = null, office = null, people.take(1))
        }
        val resultUsage = usage ?: VehicleUsage.SOLO
        val hasHomeOffice = home != null && office != null
        return FirstRunResult(
            usage = resultUsage,
            defaultCategory = defaultCategory,
            home = home.takeIf { hasHomeOffice },
            office = office.takeIf { hasHomeOffice },
            people = if (resultUsage.isShared) people else people.take(1),
        )
    }
}

data class FirstRunResult(
    val usage: VehicleUsage,
    val defaultCategory: Category,
    val home: TripPlace?,
    val office: TripPlace?,
    val people: List<NewPerson>,
)

/** 저장 전의 사람 (id 는 저장할 때 정해진다) */
data class NewPerson(val name: String, val initial: String, val dept: String = "")
