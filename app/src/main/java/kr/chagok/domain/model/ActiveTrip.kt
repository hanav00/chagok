package kr.chagok.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * 지금 달리고 있는 운행. 출발 때 디스크에 열고, 계기판이 바뀔 때마다 [lastKm] 을 덮어쓰고, 주차하면 닫는다.
 * 꺼지는 순간에 저장하지 않으므로, 어떻게 꺼지든 다음 시작 때 마지막 저장값으로 마감한다.
 *
 * @property destination 주행 중 내비에서 받은 목적지 (업무 운행만)
 * @property isEditedWhileDriving 주행 중 Gleo 음성으로 분류를 바꿨다. 주차하면 분류 근거가 "사용자 수정"이 된다
 */
data class ActiveTrip(
    val date: LocalDate,
    val startKm: Double,
    val startedAt: LocalTime,
    val lastKm: Double,
    val lastAt: LocalTime,
    val departure: NextDeparture,
    val origin: TripPlace?,
    val destination: TripPlace?,
    val isEditedWhileDriving: Boolean = false,
)
