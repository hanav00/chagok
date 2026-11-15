package kr.chagok.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kr.chagok.recorder.RecorderService

/**
 * [디버그 전용] 시동(부팅) 때와 같이 기록 서비스를 띄우고 "업무로 기록됩니다"를 한 번 말한다.
 * 에뮬레이터는 시연 중에 다시 부팅할 수 없어서 그 순간을 흉내 낸다. 앱을 새로 깔아 서비스가 내려가 있어도 기록이 다시 돈다.
 *
 *   adb shell am broadcast --user 10 -a kr.chagok.demo.IGNITION -n kr.chagok/.demo.DemoIgnitionReceiver
 */
class DemoIgnitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RecorderService.start(context, isIgnition = true)
    }
}
