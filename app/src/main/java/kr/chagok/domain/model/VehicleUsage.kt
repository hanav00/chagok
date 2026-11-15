package kr.chagok.domain.model

/** 누가 쓰는 차인지. 첫 실행에서 고르고 설정에서 바꾼다 */
enum class VehicleUsage {
    SOLO,
    SHARED,
    CHAUFFEUR;

    /** 여러 명이 타는 차: 출발 전에 "누가 타나요?"를 묻고, 운행마다 사람을 비운다 */
    val isShared: Boolean get() = this != SOLO
}
