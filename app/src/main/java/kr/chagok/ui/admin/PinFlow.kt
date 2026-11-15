package kr.chagok.ui.admin

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kr.chagok.R
import kr.chagok.data.admin.AdminRepository
import kr.chagok.domain.ChagokConfig
import kr.chagok.ui.UserMessages

/** PIN 시트 단계: 넣기 · 복구키 · 새 PIN · 한 번 더 · 새 복구키 보여 주기 */
internal enum class PinStage {
    PIN,
    RECOVER,
    NEW,
    CONFIRM,
    KEY;

    /** 이 단계에서 넣을 자리 수. 복구키도 숫자라 같은 숫자판으로 넣는다 */
    val inputLength: Int
        get() = if (this == RECOVER) ChagokConfig.RECOVERY_KEY_LENGTH else ChagokConfig.PIN_LENGTH
}

/** 숫자판 위 빨간 한 줄. 너무 많이 틀린 것은 관리자 상태로 따로 보여 준다 */
internal enum class PinMessage { WRONG, RECOVER_WRONG, MISMATCH }

/** 새 복구키를 만드는 모드 (처음 정하기·넘기기). 나머지는 복구키를 그대로 둔다 */
private val PinMode.issuesNewKey: Boolean get() = this == PinMode.SETUP || this == PinMode.HANDOVER

/**
 * PIN 시트가 한 번 열린 동안의 단계 흐름. 지난번 입력·복구키가 남지 않게 ViewModel 대신 시트가 열 때마다 만든다.
 * 자리 수를 다 채우면 바로 확인하고, 16자리 복구키만 [submitRecovery] 를 기다린다.
 *
 * @param onFinish PIN 이 맞았거나 새로 정하고 복구키까지 확인했을 때
 */
@Stable
internal class PinFlow(
    val mode: PinMode,
    private val admin: AdminRepository,
    private val messages: UserMessages,
    private val scope: CoroutineScope,
    private val onFinish: () -> Unit,
) {
    var stage by mutableStateOf(if (mode == PinMode.UNLOCK) PinStage.PIN else PinStage.NEW)
        private set
    var input by mutableStateOf("")
        private set
    var message by mutableStateOf<PinMessage?>(null)
        private set

    /** 방금 만든 복구키. [PinStage.KEY] 에서 한 번만 보여 준다 */
    var newKey by mutableStateOf("")
        private set

    private var firstPin = ""

    // 확인하는 동안 들어온 숫자는 버린다 (다음 단계 입력에 섞이지 않게)
    private var isChecking = false

    fun press(digit: Char) {
        if (isChecking || input.length >= stage.inputLength) return
        input += digit
        message = null
        if (input.length == stage.inputLength && stage != PinStage.RECOVER) onComplete()
    }

    /** 복구키를 다 넣었는지 ("확인" 버튼을 켤지) */
    val isRecoveryFilled: Boolean get() = stage == PinStage.RECOVER && input.length == stage.inputLength

    /** 복구키 "확인" */
    fun submitRecovery() {
        if (isChecking || !isRecoveryFilled) return
        onComplete()
    }

    fun delete() {
        if (isChecking) return
        input = input.dropLast(1)
        message = null
    }

    /** "PIN을 잊었어요" → 복구키 넣기. PIN 확인 중이면 무시한다 (확인 결과가 다시 PIN 단계로 끌어낸다) */
    fun startRecovery() {
        if (!isChecking) goStage(PinStage.RECOVER)
    }

    /** 복구키 단계에서 "PIN으로 돌아가기" */
    fun backToPin() {
        if (!isChecking) goStage(PinStage.PIN)
    }

    /** 새 복구키를 "적어 뒀어요": 이때 새 PIN·복구키를 함께 저장한다 */
    fun confirmKeySaved() {
        if (isChecking) return
        verify {
            admin.setPinAndKey(firstPin, newKey)
            onFinish()
        }
    }

    private fun onComplete() {
        val entered = input
        when (stage) {
            PinStage.PIN -> verify {
                if (admin.unlock(entered)) onFinish() else goStage(PinStage.PIN, PinMessage.WRONG)
            }
            PinStage.RECOVER -> verify {
                // 틀리면 넣은 숫자는 남겨 둔다 (16자리 중 틀린 곳만 고치게)
                if (admin.checkRecoveryKey(entered)) {
                    goStage(PinStage.NEW)
                } else {
                    message = PinMessage.RECOVER_WRONG
                }
            }
            PinStage.NEW -> {
                firstPin = entered
                goStage(PinStage.CONFIRM)
            }
            PinStage.CONFIRM -> when {
                entered != firstPin -> goStage(PinStage.NEW, PinMessage.MISMATCH)
                // 처음 정하기·넘기기: 새 복구키를 먼저 보여 주고, "적어 뒀어요"를 눌러야 저장한다.
                // 그 전에 시트가 닫히면 예전 PIN·복구키가 그대로라 키를 놓쳐도 관리자 권한을 잃지 않는다
                mode.issuesNewKey -> {
                    newKey = admin.newRecoveryKey()
                    goStage(PinStage.KEY)
                }
                // 바꾸기·복구키로 다시 정하기: 복구키는 그대로
                else -> verify {
                    admin.changePin(entered)
                    onFinish()
                }
            }
            PinStage.KEY -> Unit
        }
    }

    private fun goStage(next: PinStage, nextMessage: PinMessage? = null) {
        stage = next
        input = ""
        message = nextMessage
    }

    // 확인·저장이 실패하면 로그를 남기고 알린 뒤 같은 단계를 처음부터 다시 받는다
    private fun verify(block: suspend () -> Unit) {
        isChecking = true
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "관리자 PIN 확인·저장 실패", error)
                messages.show(R.string.error_save)
                goStage(stage)
            } finally {
                isChecking = false
            }
        }
    }

    private companion object {
        const val TAG = "PinFlow"
    }
}
