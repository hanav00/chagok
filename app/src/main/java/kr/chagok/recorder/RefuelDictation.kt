package kr.chagok.recorder

import kr.chagok.car.VoiceInput
import kr.chagok.data.RefuelRepository
import kr.chagok.domain.SpokenAmount

/** 주유·충전 금액 받아쓰기: 한 번 듣고 "5만 4천 원"을 금액으로 남긴다. 못 알아들으면 아무것도 바꾸지 않는다 */
class RefuelDictation(
    private val voiceInput: () -> VoiceInput,
    private val refuels: RefuelRepository,
) {
    sealed interface Result {
        /** 남긴 금액 (원). 화면에 "○원 남겼어요"로 보인다 */
        data class Done(val won: Long) : Result

        data object NotHeard : Result
    }

    suspend fun dictate(refuelId: Long): Result {
        val spoken = voiceInput().listen() ?: return Result.NotHeard
        val won = SpokenAmount.parse(spoken) ?: return Result.NotHeard
        refuels.setAmount(refuelId, won)
        return Result.Done(won)
    }
}
