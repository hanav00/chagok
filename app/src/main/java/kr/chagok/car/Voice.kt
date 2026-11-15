package kr.chagok.car

/** 짧은 음성 안내 (도착 때 "○○ 도착, 업무로 기록했어요"). 실제로는 [PleosVoiceOutput] */
interface VoiceOutput {
    fun speak(text: String)

    fun release()
}

/** 음성 한마디 듣기 (방문 목적·사람 이름). 실제로는 [PleosVoiceInput] */
interface VoiceInput {
    /** 한 번 듣고 글자로 돌려준다. 못 알아들었거나 시간이 지나면 null */
    suspend fun listen(): String?

    fun release()
}
