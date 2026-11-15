package kr.chagok.ui

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kr.chagok.AppContainer
import kr.chagok.ChagokApplication
import kr.chagok.R
import kr.chagok.domain.ChagokConfig
import kr.chagok.recorder.TripDictation

/** 화면 상태 공유 방식. 화면이 사라져도 5초는 살려 둬 회전·창 크기 변경 때 다시 읽지 않는다 */
val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(5_000)

/** ViewModel 을 앱 컨테이너로 만드는 팩토리: `viewModel(factory = containerFactory(::HomeViewModel))` */
inline fun <reified VM : ViewModel> containerFactory(
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory { initializer { create(appContainer()) } }

/** ViewModel 없이 컨테이너가 필요한 화면(관리자 PIN 시트)에서 쓴다 */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as ChagokApplication).container

fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ChagokApplication).container

/**
 * 앱 화면 아래 잠깐 띄우는 알림 한 줄 (쓰기 실패, 음성을 못 알아들음, 말로 넣은 값이 남음).
 * [retry] 가 있으면 줄에 "다시 하기"가 붙는다. [isDone] 이면 경고 대신 체크 표시로 보인다. 같은 문구라도 매번 새 알림이다
 */
class UserMessage(
    @StringRes val text: Int,
    val retry: (() -> Unit)? = null,
    val args: List<Any> = emptyList(),
    val isDone: Boolean = false,
)

/**
 * 알림을 차례로 쌓아 두는 곳. 껍데기([kr.chagok.ui.shell.ChagokApp])가 하나씩 꺼내 보여 준다.
 * 받는 쪽이 없을 때(주행 중, 화면이 잠깐 내려간 사이)에도 [ChagokConfig.NOTICE_QUEUE_SIZE] 개까지 남겨 두고, 넘치면 오래된 것부터 버린다
 */
class UserMessages {
    private val queue = Channel<UserMessage>(
        capacity = ChagokConfig.NOTICE_QUEUE_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 받는 쪽은 하나뿐이다 (꺼낸 알림은 큐에서 빠진다) */
    val messages: Flow<UserMessage> = queue.receiveAsFlow()

    fun show(@StringRes text: Int, retry: (() -> Unit)? = null) {
        queue.trySend(UserMessage(text, retry))
    }

    /** 말로 넣은 값이 남았다고 알린다 (예: "충전 32,000원 남겼어요") */
    fun showDone(@StringRes text: Int, vararg args: Any) {
        queue.trySend(UserMessage(text, args = args.toList(), isDone = true))
    }
}

/**
 * 저장하는 일을 돌린다. 실패하면 로그를 남기고 "저장하지 못했어요 · 다시 하기"를 띄운다.
 * 다시 하기는 [retry], 없으면 같은 일을 한 번 더 돌린다. 한 번 더 해도 결과가 같은 일만 [retry] 를 생략하고,
 * 권한·화면 상태를 다시 봐야 하거나 두 번 하면 안 되는 일은 [retry] 로 자기 함수를 부른다.
 */
fun ViewModel.launchSaving(messages: UserMessages, retry: (() -> Unit)? = null, block: suspend () -> Unit): Job {
    val tag = javaClass.simpleName // launch 안에서 javaClass 는 코루틴(StandaloneCoroutine)을 가리킨다
    return viewModelScope.launch {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(tag, "저장 실패", error)
            val again: () -> Unit = retry ?: { launchSaving(messages, block = block) }
            messages.show(R.string.error_save_retryable, again)
        }
    }
}

/**
 * 마이크 받아쓰기: 한 번 듣고 [tripId] 운행을 고친다 ([kr.chagok.recorder.TripDictation]). 듣는 중에 다시 누르면 무시하고,
 * 못 알아들었거나 바꿀 수 없는 말이면 알린다. 관리자 여부는 누른 그 순간의 상태로 정한다.
 * 저장 실패의 "다시 하기"도 이리로 와서, 그사이 카드나 시트가 다른 운행으로 바뀌어도 처음 누른 운행을 고친다.
 */
fun ViewModel.launchTripDictation(
    container: AppContainer,
    tripId: Long,
    isListening: () -> Boolean,
    setListening: (Boolean) -> Unit,
) {
    if (isListening()) return
    setListening(true)
    val admin = container.admin.state.value
    launchSaving(container.messages, retry = { launchTripDictation(container, tripId, isListening, setListening) }) {
        try {
            dictationMessage(container.tripDictation.dictate(tripId, admin))?.let(container.messages::show)
        } finally {
            setListening(false)
        }
    }
}

@StringRes
private fun dictationMessage(result: TripDictation.Result): Int? = when (result) {
    TripDictation.Result.DONE -> null
    TripDictation.Result.NOT_HEARD -> R.string.voice_not_heard
    TripDictation.Result.NOT_ALLOWED -> R.string.voice_not_allowed
    TripDictation.Result.PERSON_LOCKED -> R.string.voice_person_locked
    TripDictation.Result.PERSON_AMBIGUOUS -> R.string.voice_person_ambiguous
}
