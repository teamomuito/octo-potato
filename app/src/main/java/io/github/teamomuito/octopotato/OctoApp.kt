package io.github.teamomuito.octopotato

import android.app.Application
import io.github.teamomuito.octopotato.data.Prefs
import io.github.teamomuito.octopotato.work.Jobs
import io.github.teamomuito.octopotato.work.Notifier

class OctoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Notifier.createChannel(this)
        Jobs.ensureScheduled(this)
    }
}
