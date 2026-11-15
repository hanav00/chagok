package kr.chagok.car

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * Pleos TextToSpeech (온디바이스). 네트워크·앱 등록 없이 백그라운드 서비스에서도 말한다.
 * initialize() 전에 addEventListener() 를 부르면 SDK 안에서 앱이 죽는다. 초기화 성공 뒤에 붙인다.
 * 준비 전에 온 요청은 마지막 한 문장만 들고 있다가 준비되면 말한다.
 */
class PleosVoiceOutput(context: Context) : VoiceOutput {
    private val tts = TextToSpeech(context, Mode.ON_DEVICE)

    // 초기화 콜백과 speak 가 다른 스레드에서 온다. 둘이 엇갈려 대기 문장을 잃지 않게 함께 묶는다
    private val lock = Any()
    private var isReady = false
    private var pending: String? = null

    private val events = object : EventListener {
        override fun onError(type: ErrorType) {
            Log.w(TAG, "TTS 오류 $type")
        }

        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onDone() = Unit
        override fun onStop() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.addEventListener(events)
                runCatching { tts.setLocale(Locale.KOREA) }
                val queued = synchronized(lock) {
                    isReady = true
                    pending.also { pending = null }
                }
                queued?.let(::speak)
            }

            override fun onFailure(type: ErrorType) {
                Log.e(TAG, "TTS 초기화 실패 $type")
            }
        })
    }

    override fun speak(text: String) {
        synchronized(lock) {
            if (!isReady) {
                pending = text
                return
            }
        }
        // isFinal 이 false 면 엔진이 이어질 문장을 기다리며 말하지 않는다
        runCatching { tts.speak(text, isFinal = true) }.onFailure { Log.w(TAG, "TTS 요청 실패", it) }
    }

    override fun release() {
        runCatching { tts.removeEventListener(events) }
        tts.release()
    }

    private companion object {
        const val TAG = "PleosVoiceOutput"
    }
}
