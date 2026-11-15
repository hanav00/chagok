package kr.chagok

import android.app.Application

class ChagokApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(resources, AppEnvironment.real(this))
    }
}
