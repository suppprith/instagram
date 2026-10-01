package com.suppprith.dms.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.suppprith.dms.graph
import com.suppprith.dms.notify.Notifications
import kotlinx.coroutines.launch

/** Pass expiry alarm and the notification's "Lock now" action. */
class RelockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_EXPIRE && action != ACTION_LOCK_NOW) return
        val pending = goAsync()
        val graph = context.graph
        graph.scope.launch {
            try {
                Passes.end(context.applicationContext, reason = if (action == ACTION_LOCK_NOW) "lock now" else "alarm")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_EXPIRE = "com.suppprith.dms.action.PASS_EXPIRE"
        const val ACTION_LOCK_NOW = "com.suppprith.dms.action.LOCK_NOW"
    }
}

/** Starting and ending passes, shared by the lock sheet, the alarm and the service. */
object Passes {
    suspend fun start(context: Context, reason: PassReason): PassState? {
        val graph = context.graph
        val state = graph.passes.startPass(reason) ?: return null
        val endsAt = state.endsAt ?: return null
        RelockScheduler.schedule(context, endsAt)
        Notifications.showPass(context, endsAt)
        graph.log.i("lock", "pass started (${state.used} used today)")
        return state
    }

    /** Ends the running pass and sends the user home if the Instagram app is in front. */
    suspend fun end(context: Context, reason: String) {
        val graph = context.graph
        graph.passes.endPass()
        RelockScheduler.cancel(context)
        Notifications.cancelPass(context)
        LockAccessibilityService.instance?.onPassEnded()
        graph.log.i("lock", "pass ended: $reason")
    }
}
