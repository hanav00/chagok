package kr.chagok.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 색 토큰. 색은 여기에만 쓴다. 반투명은 토큰에 alpha 를 준다: ChagokColors.accent.copy(alpha = 0.12f)
 *
 * 색은 보라와 무채색만 쓴다. 빨강([red])은 오류, [gleo] 는 Gleo 쪽 일에만 남긴다.
 * 누르는 것·고른 것·링크는 파스텔 보라 강조색([accent]). 업무 블록도 같은 보라라, 다음 출발에서 고른 "업무"와 맞물린다.
 * 분류 색([commute]·[business]·[personal]·[pending])은 블록·칩·막대·점처럼 분류를 나타낼 때만 쓴다.
 * 숫자(업무사용비율·절세 금액)는 보라면 누를 수 있는 것처럼 보여서 흰색([text])이다.
 */
object ChagokColors {
    // 배경
    val window = Color(0xFF16181B)
    val section = Color(0xFF202226)
    val card = Color(0xFF2A2C30)
    val cardStrong = Color(0xFF34373C)
    val divider = Color.White.copy(alpha = 0.08f)
    val scrim = Color.Black.copy(alpha = 0.55f)

    /** 화면 아래 알림 줄. 시트(card) 위에 떠도 구분되게 [cardStrong] 보다 한 단계 밝게 */
    val notice = Color(0xFF41444A)

    /** 슬라이더 손잡이·QR 바탕처럼 순백이 필요한 곳 */
    val white = Color.White

    // 글자
    val text = Color(0xFFF2F3F5)
    val textSub = Color(0xFF9A9EA6)
    /** 가장 흐린 글자. 바탕(section) 위 대비 4.5:1 이상 (차 안 햇빛에서도 읽히게) */
    val textDim = Color(0xFF888C94)

    // 포인트
    /** 파스텔 보라. 바탕(window) 대비 9.7:1, 위에 얹는 [onAccent] 대비 10.7:1 */
    val accent = Color(0xFFC3B8F2)

    /** 고른 칸의 옅은 바탕 ([kr.chagok.ui.components.selectedFrame]) */
    val selectedTint = accent.copy(alpha = 0.12f)
    /** 끝남·이어짐 확인 표시. 보라와 무채색만 쓰므로 강조색과 같다 */
    val green = Color(0xFFC3B8F2)
    val red = Color(0xFFEF8E8E)
    val redText = Color(0xFFF6B1B1)

    // 분류: 업무 연보라 · 출퇴근 진보라 · 개인 짙은 회색 · 분류 대기 회색. 위에 얹는 on* 글자 대비 큰 글자 기준 3:1 이상(개인 4:1, 나머지 4.5:1 이상)
    val commute = Color(0xFF8A7FC4)
    val business = Color(0xFFC3B8F2)
    val personal = Color(0xFF6E7279)
    val pending = Color(0xFF9A9EA6)

    // 분류 색 계열의 밝은 글자 (어두운 바탕 위)
    val businessText = Color(0xFFD9D2F8)
    /** 확인할 것(분류 대기·누가 탔나요·입력 오류). 주황 대신 밝은 흰색으로 세운다 */
    val pendingText = Color(0xFFE6E7EA)
    val personalText = Color(0xFFC9CCD1)

    // 밝은 바탕 위 어두운 글자
    val onAccent = Color(0xFF0B0C0E)
    val onBusiness = Color(0xFF1B1640)
    val onCommute = Color(0xFF0B0C0E)
    val onPending = Color(0xFF0B0C0E)
    val onBright = Color(0xFF0B0C0E)

    /** 사람 얼굴 원 바탕. 분류 색과 헷갈리지 않게 회색 하나 (사람은 글자로 가린다) */
    val avatar = Color(0xFF565A62)

    /** Gleo 를 기다리는 표시. 앱 강조색과 달라야 앱이 아니라 Gleo 쪽 일로 보인다 */
    val gleo = Color(0xFF8FD3FF)
}
