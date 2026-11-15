package kr.chagok.data

import android.util.Log
import kr.chagok.common.runSuspendCatching

/** 쓰기 실패는 로그를 남기고 호출부로 넘긴다. 화면이 "저장하지 못했어요 · 다시 하기"로 알린다 */
internal inline fun <T> writeOrThrow(tag: String, what: String, block: () -> T): T =
    runSuspendCatching(block).onFailure { Log.e(tag, "$what 실패", it) }.getOrThrow()

/** 읽기 실패는 로그를 남기고 [fallback] 으로 이어 간다. 화면 전체가 멈추는 것보다 낫다 */
internal inline fun <T> readOrDefault(tag: String, what: String, fallback: T, block: () -> T): T =
    runSuspendCatching(block).onFailure { Log.e(tag, "$what 실패", it) }.getOrDefault(fallback)
