package kr.chagok.domain.model

import java.util.UUID

/**
 * 기록 한 건(운행·사람·관리자 수정)의 전역 식별값 (UUID 문자열). 차 안 DB 의 id(자동 증가 Long)는 그 차 안에서만
 * 유일하므로, 여러 차의 기록을 합치거나 백업을 되살릴 때는 이 값으로 같은 기록인지 가린다. 한 번 정하면 바꾸지 않는다.
 */
object RecordUid {
    fun new(): String = UUID.randomUUID().toString()
}
