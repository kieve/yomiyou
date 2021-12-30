package ca.kieve.yomiyou

import android.app.Application
import ca.kieve.yomiyou.data.AppContainer

class YomiApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
