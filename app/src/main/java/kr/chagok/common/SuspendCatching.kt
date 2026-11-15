package kr.chagok.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] 과 같지만 코루틴 취소는 잡지 않고 그대로 던진다.
 * 취소를 실패로 삼키면 멈춰야 할 일이 이어서 돌고, 로그에는 엉뚱한 "실패"가 남는다.
 */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
