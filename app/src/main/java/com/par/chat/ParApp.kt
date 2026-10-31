package com.par.chat

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.par.chat.call.CallManager
import com.par.chat.data.ChatEngine
import com.par.chat.data.EngineEvent
import com.par.chat.data.Session
import com.par.chat.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ParApp : Application() {
    lateinit var store: Store
    lateinit var engine: ChatEngine
    lateinit var calls: CallManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    private val _theme = MutableStateFlow("auto")
    val theme: StateFlow<String> = _theme
    fun setTheme(v: String) { store.saveTheme(v); _theme.value = v }

    private var started = 0
    /** True while one of our screens is visible. Notifications are only needed when it is false. */
    val inForeground: Boolean get() = started > 0

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        _theme.value = store.theme()
        engine = ChatEngine(this, store)
        calls = CallManager(this, engine, store)
        Notifier.ensureChannels(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { started++ }
            override fun onActivityStopped(a: Activity) { started-- }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        // Message notifications while the app is in the background (or another chat is open).
        scope.launch {
            engine.events.collect { e ->
                if (e !is EngineEvent.Incoming) return@collect
                val st = engine.state.value
                if (inForeground && st.active == e.key) return@collect
                val sender = st.users[e.msg.sender_id]?.display_name ?: "?"
                val title = if (e.key.startsWith("g:")) {
                    val g = st.groups.firstOrNull { it.id == e.key.drop(2) }?.let { if (it.id == "general") getString(R.string.general_group) else it.name } ?: ""
                    "$sender · $g"
                } else sender
                val text = when {
                    e.msg.file?.kind == "image" -> getString(R.string.pv_photo)
                    e.msg.file?.kind == "voice" -> getString(R.string.voice_message)
                    e.msg.file != null -> e.msg.file.name
                    else -> e.msg.body
                }
                Notifier.message(this@ParApp, title, text, e.key)
            }
        }
        store.loadSession()?.let { signIn(it, remember = false) }
    }

    fun signIn(s: Session, remember: Boolean = true) {
        if (remember) store.saveSession(s)
        _session.value = s
        engine.start(s)
        KeepAliveService.setCall(this, 0)
    }

    fun signOut() {
        calls.end()
        engine.stop()
        store.saveSession(null)
        _session.value = null
        KeepAliveService.stop(this)
    }
}
