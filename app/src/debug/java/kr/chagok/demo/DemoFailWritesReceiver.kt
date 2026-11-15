package kr.chagok.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kr.chagok.data.db.ChagokDatabase

/**
 * [디버그 전용] 운행 고치기(분류·사람·비고) 저장을 일부러 실패하게 만든다. 화면 아래 알림 줄과 "다시 하기"를 볼 때 쓴다.
 * 끄기 전까지 앱을 껐다 켜도 계속 실패한다 (DB 트리거로 막는다).
 *
 *   adb shell am broadcast --user 10 -a kr.chagok.demo.FAIL_WRITES --ez on true \
 *     -n kr.chagok/.demo.DemoFailWritesReceiver
 *   adb shell am broadcast --user 10 -a kr.chagok.demo.FAIL_WRITES --ez on false \
 *     -n kr.chagok/.demo.DemoFailWritesReceiver
 */
class DemoFailWritesReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val isOn = intent.getBooleanExtra(EXTRA_ON, true)
        // 앱의 Room 과 따로 열어도 트리거는 DB 파일에 남아 앱 연결에도 걸린다
        val path = context.getDatabasePath(ChagokDatabase.FILE_NAME).path
        runCatching {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("DROP TRIGGER IF EXISTS $TRIGGER")
                if (isOn) {
                    db.execSQL("CREATE TRIGGER $TRIGGER BEFORE UPDATE ON trips BEGIN SELECT RAISE(ABORT, 'demo'); END")
                }
            }
        }.onSuccess { Log.i(TAG, if (isOn) "운행 고치기 저장을 막았음" else "운행 고치기 저장을 풀었음") }
            .onFailure { Log.e(TAG, "저장 막기 바꾸기 실패", it) }
    }

    private companion object {
        const val TAG = "DemoFailWrites"
        const val EXTRA_ON = "on"
        const val TRIGGER = "demo_fail_trip_update"
    }
}
