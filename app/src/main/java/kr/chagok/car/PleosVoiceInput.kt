package kr.chagok.car

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Pleos SpeechToText (온디바이스). 한 번 듣고 마지막 결과를 돌려준다. TTS 처럼 초기화가 끝난 뒤에 리스너를 붙인다.
 */
class PleosVoiceInput(context: Context) : VoiceInput {
    private val stt = SpeechToText(context, Mode.ON_DEVICE)
    private val ready = CompletableDeferred<Boolean>()
    @Volatile
    private var result: CompletableDeferred<String?>? = null
    // 이번에 듣는 동안 들어온 마지막 중간 결과
    @Volatile
    private var latest: String? = null
    private val turn = Mutex()

    private val listener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            val heard = text.trim().ifEmpty { null }
            if (isFinal) result?.complete(heard) else if (heard != null) latest = heard
        }

        override fun onError(type: ErrorType) {
            Log.w(TAG, "STT 오류 $type")
            result?.complete(null)
        }

        // SDK 가 최종 결과보다 끝났다는 알림을 먼저 보낼 때가 있다. 그때는 마지막 중간 결과로 정한다
        override fun onEndedRecognition() {
            result?.complete(latest)
        }

        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                stt.addListener(listener)
                runCatching { stt.setLocale(Locale.KOREA) }
                ready.complete(true)
            }

            override fun onFailure(type: ErrorType) {
                Log.e(TAG, "STT 초기화 실패 $type")
                ready.complete(false)
            }
        })
    }

    /**
     * 결과를 받는 자리([result])가 하나라 한 번에 하나만 듣는다. 이미 듣는 중이면 바로 null.
     * 기다리다 취소돼도(화면이 닫힘) 마이크를 끈다.
     */
    override suspend fun listen(): String? {
        if (!turn.tryLock()) {
            Log.w(TAG, "이미 듣는 중이라 이번 요청은 넘김")
            return null
        }
        try {
            // 초기화 콜백이 끝내 오지 않아도 "듣고 있어요"가 끝없이 남지 않게 시간 제한을 둔다
            val isReady = withTimeoutOrNull(CarConfig.LISTEN_TIMEOUT_MILLIS) { ready.await() } ?: false
            if (!isReady) return null
            latest = null
            val waiting = CompletableDeferred<String?>().also { result = it }
            var heard: String? = null
            try {
                stt.request()
                heard = withTimeoutOrNull(CarConfig.LISTEN_TIMEOUT_MILLIS) { waiting.await() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "STT 요청 실패", error)
            } finally {
                if (heard == null) runCatching { stt.stop() }
                result = null
            }
            return heard
        } finally {
            turn.unlock()
        }
    }

    override fun release() {
        runCatching { stt.removeListener(listener) }
        stt.release()
    }

    private companion object {
        const val TAG = "PleosVoiceInput"
    }
}
