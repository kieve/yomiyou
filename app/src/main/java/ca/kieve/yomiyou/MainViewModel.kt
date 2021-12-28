package ca.kieve.yomiyou

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ca.kieve.yomiyou.crawler.source.en.l.LightNovelPub

class MainViewModel(application: Application): AndroidViewModel(application) {
    val perfectRun = "the-perfect-run"
    val swordGod = "reincarnation-of-the-strongest-sword-god-lnv-24121303"
    val crawler = LightNovelPub(
        "https://www.lightnovelpub.com/",
        "https://www.lightnovelpub.com/novel/$swordGod")
}