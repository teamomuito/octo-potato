package io.github.teamomuito.octopotato.work

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Everything that runs in the background, in one place. */
object Jobs {
    private const val INDEX = "index"
    private const val WATCH = "watch"
    private const val TIDY = "tidy"

    fun ensureScheduled(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            TIDY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TidyWorker>(1, TimeUnit.DAYS).setInitialDelay(2, TimeUnit.HOURS).build(),
        )
        watch(context, ExistingWorkPolicy.KEEP)
        indexNow(context)
    }

    /** Read whatever hasn't been read yet. With [queue] it runs after the current pass instead of being dropped. */
    fun indexNow(context: Context, queue: Boolean = false) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            INDEX,
            if (queue) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IndexWorker>().build(),
        )
    }

    /** Wakes up when something new lands in the photo library. It re-arms itself each time it fires. */
    fun watch(context: Context, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(Duration.ofSeconds(5))
            .setTriggerContentMaxDelay(Duration.ofMinutes(1))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WATCH,
            policy,
            OneTimeWorkRequestBuilder<WatchWorker>().setConstraints(constraints).build(),
        )
    }
}
