package io.github.teamomuito.octopotato.work

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.teamomuito.octopotato.data.Access
import io.github.teamomuito.octopotato.data.Media
import io.github.teamomuito.octopotato.data.ShotDb
import io.github.teamomuito.octopotato.scan.Classifier
import io.github.teamomuito.octopotato.scan.Reader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads unread screenshots until there are none left, or until it's time to hand over to a fresh run. */
class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        if (Access.level(context) == Access.Level.NONE) return@withContext Result.success()

        val db = ShotDb.get(context)
        Media.sync(context, db)

        val started = SystemClock.elapsedRealtime()
        Reader(context.contentResolver).use { reader ->
            while (!isStopped && SystemClock.elapsedRealtime() - started < BUDGET_MS) {
                val batch = db.pending(20)
                if (batch.isEmpty()) break
                for (shot in batch) {
                    if (isStopped) break
                    try {
                        val reading = reader.read(shot.uri)
                        db.saveReading(shot.id, reading.text, Classifier.classify(reading.text, reading.codes))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // deleted meanwhile, corrupt, too big... skip it rather than get stuck on it
                        db.markFailed(shot.id)
                    }
                }
            }
        }

        // WorkManager gives a job about ten minutes, so long backlogs continue in a new run
        if (!isStopped && db.pendingCount() > 0) Jobs.indexNow(context, queue = true)
        Result.success()
    }

    private companion object {
        const val BUDGET_MS = 8 * 60 * 1000L
    }
}
