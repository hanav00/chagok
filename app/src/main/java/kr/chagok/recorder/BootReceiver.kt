package kr.chagok.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 시동(부팅) 직후 기록 서비스를 띄운다. 안드로이드는 앱을 한 번도 열지 않은 상태로는 이 신호를 주지 않는다.
 * TODO: 실제 차에서도 시동마다 부팅 신호가 오는지 확인
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) RecorderService.start(context, isIgnition = true)
    }
}
