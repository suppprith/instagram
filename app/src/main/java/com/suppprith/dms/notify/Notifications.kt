package com.suppprith.dms.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.suppprith.dms.MainActivity
import com.suppprith.dms.R
import com.suppprith.dms.lock.RelockReceiver

object Notifications {
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_PASS = "pass"
    private const val ID_UNREAD = 1
    private const val ID_PASS = 2

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, context.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_messages_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PASS, context.getString(R.string.channel_pass), NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = context.getString(R.string.channel_pass_description)
                    setShowBadge(false)
                },
        )
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun openInbox(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_INBOX, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** "N unread conversations". Never message content. */
    fun showUnread(context: Context, count: Int) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(BadgeCount.text(count))
            .setNumber(count)
            .setContentIntent(openInbox(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        post(context, ID_UNREAD, notification)
    }

    fun cancelUnread(context: Context) = NotificationManagerCompat.from(context).cancel(ID_UNREAD)

    /** Ongoing countdown while a pass runs, with "Lock now". */
    fun showPass(context: Context, endsAt: Long) {
        if (!canPost(context)) return
        val lockNow = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, RelockReceiver::class.java).setAction(RelockReceiver.ACTION_LOCK_NOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_PASS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.pass_running))
            .setWhen(endsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setSilent(true)
            .setTimeoutAfter((endsAt - System.currentTimeMillis()).coerceAtLeast(1_000))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(0, context.getString(R.string.lock_now), lockNow)
            .build()
        post(context, ID_PASS, notification)
    }

    fun cancelPass(context: Context) = NotificationManagerCompat.from(context).cancel(ID_PASS)

    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
