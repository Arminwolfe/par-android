package com.par.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.par.chat.call.CallManager
import com.par.chat.call.CallState
import com.par.chat.data.EngineEvent
import com.par.chat.ui.CallScreen
import com.par.chat.ui.ChatScreen
import com.par.chat.ui.HomeScreen
import com.par.chat.ui.JoinScreen
import com.par.chat.ui.ParTheme
import com.par.chat.ui.callEndMessage
import com.par.chat.ui.errMessage

class MainActivity : AppCompatActivity() {
    private var sharedText by mutableStateOf<String?>(null)
    /** Conversation to open (set by tapping a message notification). */
    private var openConv by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // An incoming call must be answerable over the lock screen.
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        sharedText = textFrom(intent)
        openConv = intent.getStringExtra(Notifier.EXTRA_CONV)
        val app = application as ParApp
        setContent {
            val mode by app.theme.collectAsState()
            ParTheme(mode) { Box(Modifier.fillMaxSize()) { ParRoot(app, sharedText, openConv) { openConv = null } } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        textFrom(intent)?.let { sharedText = it }
        intent.getStringExtra(Notifier.EXTRA_CONV)?.let { openConv = it }
    }

    private fun textFrom(i: Intent?): String? =
        if (i?.action == Intent.ACTION_SEND) i.getStringExtra(Intent.EXTRA_TEXT) else null
}

private fun hasPerms(ctx: android.content.Context, perms: List<String>) =
    perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

/** Asks for microphone (and camera) permission, then runs [action]. */
@Composable
private fun rememberPermissionGate(): (video: Boolean, action: () -> Unit) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val action = pending
        pending = null
        if (result.values.all { it }) action?.invoke()
        else Toast.makeText(ctx, R.string.call_media_denied, Toast.LENGTH_LONG).show()
    }
    return { video, action ->
        val perms = if (video) listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else listOf(Manifest.permission.RECORD_AUDIO)
        if (hasPerms(ctx, perms)) action()
        else {
            pending = action
            launcher.launch(perms.toTypedArray())
        }
    }
}

@Composable
private fun ParRoot(app: ParApp, sharedText: String?, openConv: String?, onOpened: () -> Unit) {
    val ctx = LocalContext.current
    val session by app.session.collectAsState()
    val callUi by app.calls.ui.collectAsState()
    val ended by app.calls.ended.collectAsState()
    var chatKey by rememberSaveable { mutableStateOf<String?>(null) }
    val gate = rememberPermissionGate()
    val calls: CallManager = app.calls

    // Android 13+: ask once for permission to show message and call notifications.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(session) {
        if (session != null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // Tapping a message notification opens that conversation.
    LaunchedEffect(openConv, session) {
        if (openConv != null && session != null) { chatKey = openConv; onOpened() }
    }

    // Host-side events: removed from the network, or an error worth showing.
    LaunchedEffect(Unit) {
        app.engine.events.collect { e ->
            when (e) {
                is EngineEvent.Kicked -> { Toast.makeText(ctx, R.string.kicked, Toast.LENGTH_LONG).show(); chatKey = null; app.signOut() }
                is EngineEvent.Error -> if (e.code != "unknown") Toast.makeText(ctx, errMessage(e.code), Toast.LENGTH_SHORT).show()
                else -> {}
            }
        }
    }
    LaunchedEffect(ended) {
        ended?.let { Toast.makeText(ctx, callEndMessage(it), Toast.LENGTH_SHORT).show(); calls.ended.value = null }
    }

    val startCall: (String, Boolean) -> Unit = { peer, video -> gate(video) { calls.start(peer, video) } }
    val s = session

    if (s == null) {
        JoinScreen(sharedText) { joined -> app.signIn(joined) }
    } else {
        val key = chatKey
        BackHandler(enabled = key != null && callUi.state == CallState.IDLE) { chatKey = null }
        if (key == null) {
            HomeScreen(s, app.engine, app.store, onOpenChat = { chatKey = it }, onCall = startCall, onLeave = { chatKey = null; app.signOut() })
        } else {
            ChatScreen(key, s, app.engine, onBack = { chatKey = null }, onCall = startCall)
        }
    }
    if (callUi.state != CallState.IDLE) {
        CallScreen(calls, app.engine, onAccept = { gate(callUi.video) { calls.accept() } })
    }
}
