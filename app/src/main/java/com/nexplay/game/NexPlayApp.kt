package com.nexplay.game

import android.app.Application
import com.nexplay.game.data.Prefs
import com.nexplay.game.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NexPlayApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        repo = Repository(Prefs(this), scope)
    }
}
