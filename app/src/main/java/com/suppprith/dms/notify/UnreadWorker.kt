package com.suppprith.dms.notify

import android.content.Context
import android.webkit.CookieManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.suppprith.dms.graph
import com.suppprith.dms.web.UserAgent
import com.suppprith.dms.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes: ask Instagram's own badge endpoint, with the WebView's cookies, how many
 * conversations are unread, and notify when the number goes up. No server is involved and the
 * notification never contains message content.
 */
class UnreadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = applicationContext.graph
        val settings = graph.settings.current()
        if (!settings.notificationsEnabled) return Result.success()

        val cookies = withContext(Dispatchers.Main) {
            runCatching { CookieManager.getInstance().getCookie(INSTAGRAM) }.getOrNull()
        }
        if (BadgeCount.cookie(cookies, "sessionid") == null) return Result.success()

        val response = withContext(Dispatchers.IO) {
            runCatching {
                Http.get(
                    BadgeCount.ENDPOINT,
                    mapOf(
                        "Cookie" to cookies!!,
                        "X-IG-App-ID" to BadgeCount.IG_APP_ID,
                        "X-CSRFToken" to BadgeCount.cookie(cookies, "csrftoken").orEmpty(),
                        "X-Requested-With" to "XMLHttpRequest",
                        "User-Agent" to UserAgent.chromeMobile(applicationContext),
                        "Referer" to "$INSTAGRAM/direct/inbox/",
                        "Accept" to "application/json",
                    ),
                )
            }.getOrNull()
        }
        // Notifications are a bonus; any failure is silent and retried on the next period.
        val count = response?.takeIf { it.code == 200 }?.let { BadgeCount.parse(it.body) }
        if (count == null) {
            graph.log.w("unread", "badge check failed: ${response?.code ?: "network"}")
            return Result.success()
        }

        val foreground = graph.foreground.get()
        if (BadgeCount.shouldNotify(settings.lastBadge, count, foreground)) {
            Notifications.showUnread(applicationContext, count)
        }
        if (count == 0) Notifications.cancelUnread(applicationContext)
        graph.settings.setLastBadge(count)
        graph.log.i("unread", "badge $count")
        return Result.success()
    }

    companion object {
        private const val NAME = "unread"
        private const val INSTAGRAM = "https://www.instagram.com"

        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UnreadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
