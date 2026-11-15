package kr.chagok.ui.onboarding

import androidx.annotation.StringRes
import kr.chagok.R
import kr.chagok.car.NaviBookmarks
import kr.chagok.domain.FirstRunChoice
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.NewPerson
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.TripPlace
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.ui.guide.HowStory
import kr.chagok.ui.guide.PracticeState

/**
 * 첫 실행 단계.
 * 시작 → 설정 고르기(누가 쓰나요 · 사람 · 관리자 · 기본 분류 · 집·회사) → 알아보기(이렇게 기록돼요 · 말해 보기) → 완료.
 * 사람·관리자 단계는 혼자 쓰는 차면 건너뛴다.
 */
enum class OnboardingStep { WELCOME, WHO, PEOPLE, ADMIN, MODE, PLACES, HOW, TRY, DONE }

/** 집·회사 한 곳을 어떻게 정할지: 내비 즐겨찾기가 맞아요 · 지금 여기 · 안 쓸래요 */
enum class PlaceMode { BOOKMARK, HERE, SKIP }

enum class PlaceKind { HOME, OFFICE }

/** "집" · "회사". 집·회사 단계와 정리 화면이 같이 쓴다 */
@get:StringRes
internal val PlaceKind.labelRes: Int
    get() = when (this) {
        PlaceKind.HOME -> R.string.place_kind_home
        PlaceKind.OFFICE -> R.string.place_kind_office
    }

/** 등록 중인 사람. 이름은 음성으로 받거나 비워 둔다. 빈 이름은 화면에서만 "나"·"이름 없음 n"으로 보인다 */
data class DraftPerson(val name: String = "")

/** 이름 없이 등록한 사람의 이름표. 문자열 리소스라 화면이 넘겨 준다 */
class UnnamedLabels(val me: String, val numbered: (number: Int) -> String)

/** 집·회사 한 줄의 상태 */
data class PlaceRow(
    val kind: PlaceKind,
    val mode: PlaceMode,
    /** 내비 즐겨찾기 집·회사. 없으면 "맞아요"를 막는다 */
    val bookmark: TripPlace?,
    /** 다른 쪽이 이미 "지금 여기"라 이쪽은 고를 수 없다 (한 자리에 동시에 둘 다 있을 수 없다) */
    val isHereTaken: Boolean,
) {
    val hasBookmark: Boolean get() = bookmark != null

    fun canPick(mode: PlaceMode): Boolean = when (mode) {
        PlaceMode.BOOKMARK -> hasBookmark
        PlaceMode.HERE -> !isHereTaken
        PlaceMode.SKIP -> true
    }
}

/**
 * 첫 실행 화면 상태.
 * 고른 값은 "시작하기"를 누를 때까지 저장하지 않는다. 관리자 PIN 만 PIN 시트가 바로 저장한다.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val usage: VehicleUsage? = null,
    /** 첫 사람 = 이 화면을 쓰는 분 */
    val people: List<DraftPerson> = listOf(DraftPerson()),
    /** 이름을 듣고 있는 사람 자리. 듣는 동안은 더 추가하거나 다음으로 갈 수 없다 */
    val listeningIndex: Int? = null,
    /** 추천값(업무)이 미리 골라져 있다 */
    val defaultCategory: Category = Category.BUSINESS,
    val home: PlaceRow = PlaceRow(PlaceKind.HOME, PlaceMode.SKIP, bookmark = null, isHereTaken = false),
    val office: PlaceRow = PlaceRow(PlaceKind.OFFICE, PlaceMode.SKIP, bookmark = null, isHereTaken = false),
    /** "지금 여기"를 고를 때 받아 둔 현재 위치 */
    val herePlace: TripPlace? = null,
    /** "지금 여기"를 골랐는데 현재 위치를 읽지 못했다 */
    val isHereFailed: Boolean = false,
    /** "지금 여기" 현재 위치를 읽는 중 */
    val isHereLoading: Boolean = false,
    val hasPin: Boolean = false,
    /** "건너뛰기"로 정리 화면에 왔다 */
    val isSkipped: Boolean = false,
    /** Gleo 가 깔린 차. 없으면 "말해 보기"가 "1탭으로 바꿔 보기"가 된다 */
    val hasGleo: Boolean = false,
    /** "말해 보기" 연습 (그 단계에 들어가면 시작한다) */
    val practice: PracticeState? = null,
    /** "이렇게 기록돼요" 견본 운행 (고른 기본 분류로) */
    val how: HowStory = HowStory(),
) {
    val isListening: Boolean get() = listeningIndex != null

    val canAddPerson: Boolean get() = people.size < ChagokConfig.PEOPLE_MAX && !isListening

    val isHereUsed: Boolean get() = home.mode == PlaceMode.HERE || office.mode == PlaceMode.HERE

    /** 집·회사 중 한 곳만 정해졌다. 둘 다 있어야 출퇴근을 나누므로 이대로면 둘 다 안 쓴다 ([FirstRunChoice.toResult]) */
    val isOnePlaceOnly: Boolean get() = (placeOf(home) != null) != (placeOf(office) != null)

    /** 이전 단계. 혼자 쓰는 차는 사람·관리자 단계를 지나지 않았다. 첫 단계면 null */
    val previousStep: OnboardingStep?
        get() = when (step) {
            OnboardingStep.WELCOME -> null
            OnboardingStep.WHO -> OnboardingStep.WELCOME
            OnboardingStep.PEOPLE -> OnboardingStep.WHO
            OnboardingStep.ADMIN -> OnboardingStep.PEOPLE
            OnboardingStep.MODE -> if (usage == VehicleUsage.SOLO) OnboardingStep.WHO else OnboardingStep.ADMIN
            OnboardingStep.PLACES -> OnboardingStep.MODE
            OnboardingStep.HOW -> OnboardingStep.PLACES
            OnboardingStep.TRY -> OnboardingStep.HOW
            OnboardingStep.DONE -> if (isSkipped) OnboardingStep.WHO else OnboardingStep.TRY
        }

    /** 다음 단계. 정리 화면은 "다음" 대신 "시작하기"라 null */
    val nextStep: OnboardingStep?
        get() = when (step) {
            OnboardingStep.WELCOME -> OnboardingStep.WHO
            OnboardingStep.WHO -> if (usage == VehicleUsage.SOLO) OnboardingStep.MODE else OnboardingStep.PEOPLE
            OnboardingStep.PEOPLE -> OnboardingStep.ADMIN
            OnboardingStep.ADMIN -> OnboardingStep.MODE
            OnboardingStep.MODE -> OnboardingStep.PLACES
            OnboardingStep.PLACES -> OnboardingStep.HOW
            OnboardingStep.HOW -> OnboardingStep.TRY
            OnboardingStep.TRY -> OnboardingStep.DONE
            OnboardingStep.DONE -> null
        }

    val isNextEnabled: Boolean
        get() = when (step) {
            OnboardingStep.WHO -> usage != null
            OnboardingStep.PEOPLE -> people.size >= ChagokConfig.PEOPLE_MIN && !isListening
            // 위치를 읽는 동안 넘어가면 "지금 여기" 자리가 비어, 시작하기 때 집·회사가 둘 다 조용히 빠진다
            OnboardingStep.PLACES -> !isHereLoading
            else -> true
        }

    fun placeRow(kind: PlaceKind): PlaceRow = if (kind == PlaceKind.HOME) home else office

    /** 화면에 보일 이름 ("나", "이름 없음 3", 음성으로 받은 이름) */
    fun personTitle(index: Int, labels: UnnamedLabels): String {
        val name = people[index].name
        return when {
            name.isNotBlank() -> name
            index == 0 -> labels.me
            else -> labels.numbered(index + 1)
        }
    }

    /** 얼굴 원 글자: 이름 첫 글자, 이름 없이 추가한 사람은 번호 ("이름 없음 2" → "2"). 홈 얼굴 원도 이 글자를 쓴다 */
    fun personInitial(index: Int, labels: UnnamedLabels): String {
        val name = people[index].name
        return when {
            name.isNotBlank() -> name.take(1)
            index == 0 -> labels.me
            else -> (index + 1).toString()
        }
    }

    /**
     * 고른 값. 정리 화면과 "시작하기"가 같이 쓴다 ([FirstRunChoice.toResult] 가 실제로 적용될 값).
     * 이름 없는 사람은 빈 이름으로 넘긴다. "나"·"이름 없음 2"는 화면 이름표일 뿐 서식 ④성명에 찍히면 안 된다.
     */
    fun toChoice(labels: UnnamedLabels): FirstRunChoice = FirstRunChoice(
        isSkipped = isSkipped,
        usage = usage,
        defaultCategory = defaultCategory,
        home = placeOf(home),
        office = placeOf(office),
        people = people.indices.map { NewPerson(name = people[it].name.trim(), initial = personInitial(it, labels)) },
    )

    private fun placeOf(row: PlaceRow): TripPlace? = when (row.mode) {
        PlaceMode.BOOKMARK -> row.bookmark
        PlaceMode.HERE -> herePlace
        PlaceMode.SKIP -> null
    }
}

/**
 * 사용자가 고른 집·회사 방식([picks])과 내비 즐겨찾기로 실제 방식을 정한다.
 * 아직 안 골랐으면 즐겨찾기가 있을 때 "맞아요", 없으면 "안 쓸래요". 즐겨찾기가 없는데 "맞아요"일 수는 없다.
 */
internal fun placeRows(picks: Map<PlaceKind, PlaceMode>, bookmarks: NaviBookmarks): Pair<PlaceRow, PlaceRow> {
    fun modeOf(kind: PlaceKind, bookmark: TripPlace?): PlaceMode {
        val picked = picks[kind] ?: if (bookmark != null) PlaceMode.BOOKMARK else PlaceMode.SKIP
        return if (picked == PlaceMode.BOOKMARK && bookmark == null) PlaceMode.SKIP else picked
    }
    val homeMode = modeOf(PlaceKind.HOME, bookmarks.home)
    val officeMode = modeOf(PlaceKind.OFFICE, bookmarks.office)
    return PlaceRow(PlaceKind.HOME, homeMode, bookmarks.home, isHereTaken = officeMode == PlaceMode.HERE) to
        PlaceRow(PlaceKind.OFFICE, officeMode, bookmarks.office, isHereTaken = homeMode == PlaceMode.HERE)
}
