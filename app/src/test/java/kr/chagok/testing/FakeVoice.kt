package kr.chagok.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kr.chagok.car.VoiceInput
import kr.chagok.car.VoiceOutput

/**
 * 말한 문장을 모아 두는 가짜 TTS.
 * 기록기는 운행을 저장한 뒤 자기 스레드에서 말하므로, 저장을 확인한 직후에는 아직 비어 있을 수 있다.
 * 문장을 볼 때는 [spokenFlow] 를 [TestApp.await] 로 기다린다.
 */
class RecordingVoiceOutput : VoiceOutput {
    private val _spoken = MutableStateFlow<List<String>>(emptyList())
    val spokenFlow: StateFlow<List<String>> = _spoken.asStateFlow()
    val spoken: List<String> get() = _spoken.value

    override fun speak(text: String) {
        _spoken.update { it + text }
    }

    override fun release() = Unit
}

/** 정해 둔 말을 차례로 돌려주는 가짜 STT. 다 쓰면 못 알아들은 것(null) */
class ScriptedVoiceInput(vararg utterances: String?) : VoiceInput {
    private val queue = ArrayDeque(utterances.toList())

    override suspend fun listen(): String? = queue.removeFirstOrNull()

    override fun release() = Unit
}
