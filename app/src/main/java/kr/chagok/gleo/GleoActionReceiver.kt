package kr.chagok.gleo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kr.chagok.ChagokApplication
import kr.chagok.common.runSuspendCatching

/**
 * Gleo 가 `assets/schema.json` 의 함수를 부르는 브로드캐스트를 받아 [GleoCommands] 로 처리하고 회신한다.
 * 보낸 앱을 확인할 방법이 없어 아무 앱이나 같은 신호를 보낼 수 있다. 그래서 음성으로는 운전자 권한만 쓴다.
 */
class GleoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val function = GleoProtocol.functionOf(intent.action, context.packageName) ?: return
        val params = GleoProtocol.paramsOf(intent.getStringExtra(GleoProtocol.EXTRA_PARAM))
        // 방문 목적·사람 이름이 logcat 에 남지 않게 값은 빼고 인자 이름만
        Log.i(TAG, "Gleo 호출 $function ${params.keys().asSequence().toList()}")
        val commands = (context.applicationContext as ChagokApplication).container.gleo
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // 여기서 던지면 앱이 죽는다. 회신을 못 하면 Gleo 는 시간이 지나 스스로 답한다
                runSuspendCatching {
                    val reply = withTimeoutOrNull(GleoProtocol.HANDLE_TIMEOUT_MILLIS) {
                        commands.handle(function, params)
                    } ?: GleoReply.fail(TIMEOUT_MESSAGE)
                    respond(context, function, reply)
                }.onFailure { Log.e(TAG, "Gleo 회신 실패", it) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun respond(context: Context, function: String, reply: GleoReply) {
        val response = reply.toJson(function)
        context.sendBroadcast(
            Intent(GleoProtocol.ACTION_RESPONSE)
                .putExtra(GleoProtocol.EXTRA_PACKAGE, context.packageName)
                .putExtra(GleoProtocol.EXTRA_RESPONSE, response.toString()),
        )
        Log.i(TAG, "Gleo 회신 $function ${reply.status.wire}")
    }

    private companion object {
        const val TAG = "GleoActionReceiver"
        const val TIMEOUT_MESSAGE = "The logbook is busy. Please try again in a moment."
    }
}
