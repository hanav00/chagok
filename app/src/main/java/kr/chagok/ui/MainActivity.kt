package kr.chagok.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kr.chagok.ChagokApplication
import kr.chagok.recorder.RecorderService
import kr.chagok.ui.shell.ChagokApp
import kr.chagok.ui.theme.ChagokTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 설치 후 처음 열 때 기록 서비스를 띄운다. 그다음부터는 부팅 신호로 뜬다
        RecorderService.start(this)
        setContent {
            ChagokTheme {
                ChagokApp()
            }
        }
    }

    // 기기가 깊이 잠든 사이 자정이 지났을 수 있다. 화면이 다시 보이면 오늘 날짜를 다시 읽는다
    override fun onResume() {
        super.onResume()
        (application as ChagokApplication).container.today.refresh()
    }
}
