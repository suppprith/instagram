package com.suppprith.dms.lock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Ends a pass at its exact end time. Three layers, any one is enough:
 * 1. an exact alarm (or the closest inexact one when exact alarms are not allowed),
 * 2. a timer inside the running accessibility service,
 * 3. the backstop check on every accessibility event.
 */
object RelockScheduler {
    private fun intent(context: Context) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RelockReceiver::class.java).setAction(RelockReceiver.ACTION_EXPIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(context: Context, endsAt: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = intent(context)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pending)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pending)
        }
        LockAccessibilityService.instance?.scheduleExpiry(endsAt)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(intent(context))
    }
}
