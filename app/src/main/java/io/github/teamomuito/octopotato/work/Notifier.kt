package io.github.teamomuito.octopotato.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.teamomuito.octopotato.MainActivity
import io.github.teamomuito.octopotato.R
import io.github.teamomuito.octopotato.data.Access

object Notifier {
    private const val CHANNEL = "tidy"
    private const val TIDY_ID = 7

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Tidy-up reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A little note when old temporary screenshots are ready to go"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // checked by Access.canNotify
    fun tidyReady(context: Context, count: Int) {
        if (!Access.canNotify(context)) return
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_TIDY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = if (count == 1) "1 old screenshot to tidy" else "$count old screenshots to tidy"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_potato)
            .setColor(0xFFE8508F.toInt())
            .setContentTitle(title)
            .setContentText("Old qr codes, boarding passes and login codes. Tap and Potato sweeps them into the trash.")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(TIDY_ID, notification)
    }
}
