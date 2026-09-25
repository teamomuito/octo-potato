package io.github.teamomuito.octopotato.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.teamomuito.octopotato.data.Media
import io.github.teamomuito.octopotato.data.Prefs
import io.github.teamomuito.octopotato.data.ShotDb
import io.github.teamomuito.octopotato.data.TidyPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Once a day: catch up on anything the watcher missed, and if some temporary screenshots are
 * past their time, post a notification. Android only lets an app trash other apps' files from
 * the foreground, so the actual tidying happens when the app opens.
 */
class TidyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        Prefs.init(context)
        val db = ShotDb.get(context)
        Media.sync(context, db)
        Jobs.indexNow(context)
        val due = TidyPlan.due(db, Prefs.tidy.value)
        if (due.isNotEmpty()) Notifier.tidyReady(context, due.size)
        Result.success()
    }
}
