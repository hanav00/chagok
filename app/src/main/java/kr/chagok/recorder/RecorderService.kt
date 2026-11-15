package kr.chagok.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import kr.chagok.ChagokApplication
import kr.chagok.R

/**
 * 시동 중 운행을 기록하는 포그라운드 서비스. 화면을 한 번도 열지 않아도 부팅 신호로 떠서 기록한다.
 * 기록 규칙은 [TripRecorder] 에 있고, 서비스는 프로세스를 살려 두는 일만 한다.
 * 상시 알림은 Pleos 상태바·알림 센터에 보이지 않는다. 포그라운드 서비스 조건이라 둔다.
 */
class RecorderService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.recorder_channel), NotificationManager.IMPORTANCE_LOW),
        )
        // 인자 3개짜리 startForeground 는 API 29부터다 (minSdk 28). 시스템이 START_STICKY 로 다시 띄울 때
        // 백그라운드 시작 제한에 걸리면 던지므로, 앱을 죽이지 말고 로그를 남긴 뒤 서비스만 내린다
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (error: Exception) {
            Log.e(TAG, "포그라운드 시작 실패", error)
            stopSelf()
            return
        }
        (application as ChagokApplication).container.startRecording()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_IGNITION, false) == true) {
            (application as ChagokApplication).container.announceIgnition()
        }
        return START_STICKY
    }

    private fun notification(): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.recorder_notification))
        .setOngoing(true)
        .build()

    companion object {
        private const val TAG = "RecorderService"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_IGNITION = "ignition"

        /** [isIgnition] 이면 시동(부팅) 신호로 띄운 것이라 "업무로 기록됩니다"를 한 번 말한다 */
        fun start(context: Context, isIgnition: Boolean = false) {
            val intent = Intent(context, RecorderService::class.java).putExtra(EXTRA_IGNITION, isIgnition)
            runCatching { context.startForegroundService(intent) }
                .onFailure { Log.e(TAG, "기록 서비스 시작 실패", it) }
        }
    }
}
