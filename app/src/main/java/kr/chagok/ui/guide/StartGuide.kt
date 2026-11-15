package kr.chagok.ui.guide

import kr.chagok.domain.model.ChagokSettings

/** 홈 시작 가이드의 할 일. 순서가 곧 카드에 보이는 순서다 */
enum class GuideItem {
    /** 기록 방식 정하기: 첫 실행을 마치면 됐다 (건너뛰었어도 기본값으로 정해졌다) */
    RECORDING,

    /** 관리자 PIN 정하기: 공용차·기사 차만 */
    ADMIN_PIN,

    /** 집·회사 정하기: 둘 다 있어야 출퇴근을 나눈다 */
    PLACES,

    /** Gleo로 말해 보기 (Gleo 가 없는 차는 1탭으로 바꿔 보기) */
    CHANGE,

    /** 첫 운행 마치기: 운행이 하나라도 저장되면 */
    FIRST_TRIP,
}

data class GuideRow(val item: GuideItem, val isDone: Boolean)

/**
 * 홈 시작 가이드 카드. 첫 실행에서 건너뛴 것을 여기서 이어서 하고, 할 일은 실제 상태로 체크된다.
 *
 * @property hasGleo Gleo 가 깔린 차 ("말해 보기" / "1탭으로 바꿔 보기")
 */
data class StartGuide(val rows: List<GuideRow>, val hasGleo: Boolean) {
    val doneCount: Int get() = rows.count(GuideRow::isDone)

    companion object {
        /** 보일 카드. 닫았거나 다 했으면 null */
        fun of(settings: ChagokSettings, hasPin: Boolean, hasTrip: Boolean, hasGleo: Boolean): StartGuide? {
            if (settings.isGuideDismissed) return null
            val rows = buildList {
                add(GuideRow(GuideItem.RECORDING, isDone = settings.isFirstRunDone))
                if (settings.usage.isShared) add(GuideRow(GuideItem.ADMIN_PIN, isDone = hasPin))
                add(GuideRow(GuideItem.PLACES, isDone = settings.hasHomeOffice))
                add(GuideRow(GuideItem.CHANGE, isDone = settings.isChangeTried))
                add(GuideRow(GuideItem.FIRST_TRIP, isDone = hasTrip))
            }
            return StartGuide(rows, hasGleo).takeUnless { rows.all(GuideRow::isDone) }
        }
    }
}
