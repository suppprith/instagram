package com.suppprith.dms

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.suppprith.dms.cage.PatchRepository
import com.suppprith.dms.lock.PassRepository
import com.suppprith.dms.notify.Notifications
import com.suppprith.dms.notify.UnreadWorker
import com.suppprith.dms.update.UpdateChecker
import com.suppprith.dms.util.DebugLog
import com.suppprith.dms.util.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Plain constructor injection: one graph per process, no DI framework. */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val log = DebugLog(context)
    val settings = Settings(context, scope)
    val patches = PatchRepository(context, settings, log)
    val passes = PassRepository(context, scope)
    val updates = UpdateChecker(settings, log)
    val foreground = AtomicBoolean(false)
}

class App : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Notifications.createChannels(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = graph.foreground.set(true)
            override fun onStop(owner: LifecycleOwner) = graph.foreground.set(false)
        })

        // Keep the background unread check in step with the Notifications setting.
        graph.scope.launch {
            graph.settings.updates
                .map { it.notificationsEnabled }
                .distinctUntilChanged()
                .collect { enabled -> UnreadWorker.schedule(this@App, enabled) }
        }
    }
}

val Context.graph: AppGraph get() = (applicationContext as App).graph
