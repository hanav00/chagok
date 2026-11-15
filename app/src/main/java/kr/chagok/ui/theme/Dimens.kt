package kr.chagok.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 크기 토큰. 숫자는 기준 px 이고, 차 화면 dp 로 키우는 배율은 [ChagokTheme] 한 곳에서 맞춘다 (UI_SCALE).
 */
object TextSizes {
    /** 짧은 이름표·배지·단위에만 */
    val caption = 18.sp

    /** 문장(설명·안내·부제)의 하한 */
    val body = 20.sp

    val subtitle = 24.sp

    val title = 28.sp

    val headline = 34.sp

    /** 주행 중 한 줄 */
    val display = 44.sp

    /** 블록 그림과 함께 보이는 큰 표어·숫자 (첫 실행 표어, 빠진 거리) */
    val hero = 52.sp
}

object IconSizes {
    val small = 30.dp
    val large = 48.dp
}

object BorderWidths {
    /** 고른 칸의 강조색 테두리 */
    val selected = 3.dp
}

/**
 * 모서리는 두 단계만: 카드·묶음 상자 / 그 안의 버튼·칸 (바깥 여백 6px 이면 20−6=14 로 동심원이 맞는다).
 * 운행 블록 그림만 따로 모서리를 쓴다(작은 블록·큰 블록). 누르는 것이 아니라 그림이라 버튼과 달라 보여야 한다.
 */
object Shapes {
    val card = RoundedCornerShape(20.dp)
    val control = RoundedCornerShape(14.dp)
    val block = RoundedCornerShape(4.dp)
    val blockLarge = RoundedCornerShape(12.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * 간격 척도. 여백·사이 간격은 이 여덟 단계만 쓴다 (4 단위, 마지막만 48).
 * 자리에 이름이 있는 간격은 아래에 척도로 정의해 둔다.
 */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** 화면 바깥 여백 */
    val screenHorizontal = xl
    val screenTop = l
    val screenBottom = xxl

    /** 화면 안 덩어리 사이 */
    val blockGap = m

    /** 넓은 창 바깥 여백. 헤더 줄도 같은 폭이라 앱 이름과 화면 왼쪽 끝이 맞는다 */
    val wideHorizontal = xxxl
    val wideTop = xl
    val wideBottom = xxxl

    /** 넓은 창 왼쪽(누르는 것)과 오른쪽(주인공) 칸 사이. 척도 밖 값이다(48 + 24): 두 칸이 붙어 보이지 않게 */
    val wideColumnGap = xxxl + xl

    /** 넓은 창 위 줄과 아래 줄 사이 */
    val wideRowGap = xxxl

    /** 넓은 창 한 칸 안 덩어리 사이 */
    val wideBlockGap = xl

    /** 섹션 위아래 여백. 상자가 없어서 이 여백과 [blockGap] 으로만 섹션을 나눈다 */
    val sectionVertical = s

    /** 묶음 상자(세그먼트·토글) 안쪽 여백. 척도 밖 값이다: 카드 모서리 20 − 6 = 안쪽 모서리 14 로 동심원이 맞는다 */
    val groupInset = 6.dp
}

/**
 * 화면 아래 알림 줄 (저장 실패·음성 못 알아들음). Pleos UI 가이드 Toast 기준(최대 너비 792dp, 여백 24dp)에 맞춘다.
 * 이 파일 숫자는 UI_SCALE(4/3) 적용 전 값이라 나눈 값이다: 792 ÷ 4/3 = 594, 24 ÷ 4/3 = 18.
 */
object NoticeSizes {
    /** 넓은 창에서도 한눈에 읽히게 이 너비까지만 */
    val maxWidth = 594.dp

    /** 창 아래·양옆과 띄우는 거리. 척도 밖 값이다: 가이드 24dp 를 UI_SCALE 로 나눈 값 */
    val margin = 18.dp

    /** 아래 화면과 떨어져 보이게 */
    val elevation = 12.dp
}

/** 앱 창 너비 기준 (UI_SCALE 적용 전 px). 분할 칸 약 640, 넓게 약 1260 */
object WindowWidths {
    /** 이보다 넓으면 넓게 배치 (기록부 서식 전체 칸, 첫 실행 가로 배치) */
    val wide = 900.dp
}

/** 버튼 높이: 차 안에서 손가락으로 누르기 쉽게. 흔들리는 차에서도 잘못 누르지 않게 작은 버튼도 60 이상 */
object TouchSizes {
    val headButton = 60.dp
    val segment = 64.dp
    val button = 68.dp
    val round = 60.dp

    /** 나란히 놓인 작은 버튼 사이 (옆 버튼을 잘못 누르지 않게) */
    val gap = Spacing.s

    /** 글자 입력 칸: 큰 버튼과 같은 높이 */
    val input = 68.dp
}
