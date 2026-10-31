package com.par.chat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import com.par.chat.R
import com.par.chat.data.ApiException
import com.par.chat.data.ChatEngine
import com.par.chat.data.Conn
import com.par.chat.data.FileInfo
import com.par.chat.data.Msg
import com.par.chat.data.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val EMOJIS = "😀 😂 🥹 😍 😎 🤔 😅 😭 😡 👍 👎 👏 🙏 💪 🔥 ✨ ❤️ 💙 🎉 ✅ ❌ ⭐ 🌍 🚀 💡 👋 🤝 😴".split(" ")

private sealed interface Row2 {
    data class Day(val key: String, val label: String) : Row2
    data class Bubble(val m: Msg, val last: Boolean, val showSender: Boolean) : Row2
}

private fun dayOf(iso: String): LocalDate? = try {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
} catch (e: Exception) {
    null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    key: String,
    session: Session,
    engine: ChatEngine,
    onBack: () -> Unit,
    onCall: (String, Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val state by engine.state.collectAsState()
    val generalName = stringResource(R.string.general_group)
    val todayLabel = stringResource(R.string.today)
    val yesterdayLabel = stringResource(R.string.yesterday)
    val title = convTitle(key, state, generalName)
    val peer = if (key.startsWith("u:")) state.users[key.drop(2)] else null
    val msgs = state.msgs[key].orEmpty().takeLast(300) // keeps memory low
    val byId = remember(msgs) { msgs.associateBy { it.id } }
    val listState = rememberLazyListState()

    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Msg?>(null) }
    var editing by remember { mutableStateOf<Msg?>(null) }
    var menuFor by remember { mutableStateOf<Msg?>(null) }
    var emojiOpen by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recSeconds by remember { mutableStateOf(0L) }
    var pendingSave by remember { mutableStateOf<FileInfo?>(null) }
    var viewer by remember { mutableStateOf<FileInfo?>(null) }
    var chatMenu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val recorder = remember { VoiceRecorder(ctx) }

    fun toast(res: Int) = Toast.makeText(ctx, res, Toast.LENGTH_LONG).show()

    // messages with date chips between days, and "tail" info for grouping
    val rows = remember(msgs, todayLabel, yesterdayLabel) {
        val out = mutableListOf<Row2>()
        var lastDay: LocalDate? = null
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.getDefault())
        msgs.forEachIndexed { i, m ->
            val d = dayOf(m.created_at)
            if (d != null && d != lastDay) {
                val label = when (d) { today -> todayLabel; today.minusDays(1) -> yesterdayLabel; else -> fmt.format(d) }
                out.add(Row2.Day(d.toString(), label)); lastDay = d
            }
            val prev = msgs.getOrNull(i - 1)
            val next = msgs.getOrNull(i + 1)
            val first = prev == null || prev.sender_id != m.sender_id || dayOf(prev.created_at) != d
            val last = next == null || next.sender_id != m.sender_id || dayOf(next.created_at) != d
            out.add(Row2.Bubble(m, last, key.startsWith("g:") && m.sender_id != session.userId && first))
        }
        out
    }

    LaunchedEffect(key) { engine.openConv(key) }
    DisposableEffect(key) { onDispose { engine.closeConv(); recorder.cancel(); VoicePlayer.stop() } }
    LaunchedEffect(rows.size) { if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex) }
    LaunchedEffect(recording) {
        while (recording) {
            recSeconds = recorder.seconds
            delay(500)
            if (recSeconds >= 300) { // 5 minute cap
                stopRecording(recorder, engine, key, scope) { toast(it) }
                recording = false
            }
        }
    }

    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            try { engine.sendUri(key, uri) } catch (e: ApiException) { toast(errMessage(e.code)) } catch (e: Exception) { toast(R.string.err_unknown) }
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val f = pendingSave
        pendingSave = null
        if (uri != null && f != null) scope.launch {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { engine.downloadTo(f.id, it) }
                toast(R.string.saved)
            } catch (e: Exception) { toast(R.string.save_failed) }
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && recorder.start()) recording = true else toast(R.string.call_media_denied)
    }

    fun submit() {
        val body = text.trim()
        if (body.isEmpty()) return
        val ok = editing?.let { engine.edit(it.id, body) } ?: engine.send(key, body, replyTo?.id)
        if (!ok) { toast(R.string.conn_offline); return }
        text = ""; replyTo = null; editing = null
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (recorder.start()) recording = true else toast(R.string.call_media_error)
        } else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(Modifier.fillMaxSize().background(wallpaperBrush()).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // ---- floating header: round back button, title pill, round call button ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), modifier = Modifier.size(46.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            }
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), modifier = Modifier.weight(1f).height46()) {
                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(title, peer?.online, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                        val typing = engine.typingNames(key)
                        Text(
                            when {
                                state.conn != Conn.ONLINE -> stringResource(R.string.conn_offline)
                                typing.isNotEmpty() -> stringResource(R.string.typing)
                                peer != null -> stringResource(if (peer.online) R.string.online else R.string.offline)
                                else -> state.groups.firstOrNull { "g:${it.id}" == key }?.member_count?.let { "$it " + stringResource(R.string.members) } ?: ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (typing.isNotEmpty() || peer?.online == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (peer != null && peer.online && state.conn == Conn.ONLINE) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), modifier = Modifier.size(46.dp)) {
                    IconButton(onClick = { onCall(peer.id, false) }) { Icon(Icons.Default.Call, stringResource(R.string.call_audio)) }
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), modifier = Modifier.size(46.dp)) {
                    IconButton(onClick = { onCall(peer.id, true) }) { Icon(Icons.Default.Videocam, stringResource(R.string.call_video)) }
                }
            }
            if (key.startsWith("g:")) Box {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), modifier = Modifier.size(46.dp)) {
                    IconButton(onClick = { chatMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                }
                DropdownMenu(expanded = chatMenu, onDismissRequest = { chatMenu = false }) {
                    if (key != "g:general") DropdownMenuItem(text = { Text(stringResource(if (state.groups.firstOrNull { "g:${it.id}" == key }?.kind == "channel") R.string.leave_channel else R.string.leave_group)) }, onClick = { chatMenu = false; confirm = "leave" })
                    if (engine.canManage(key) && key != "g:general") DropdownMenuItem(text = { Text(stringResource(R.string.delete_group), color = MaterialTheme.colorScheme.error) }, onClick = { chatMenu = false; confirm = "delete" })
                }
            }
        }
        msgs.lastOrNull { it.pinned && !it.deleted }?.let { pm ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pinned_message), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(pm.body.ifEmpty { stringResource(R.string.attachment) }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                if (engine.canManage(key) || !key.startsWith("g:")) IconButton(onClick = { engine.pin(pm.id, false) }, Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
            }
        }

        // ---- messages ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (msgs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.say_hello), Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 14.dp, vertical = 6.dp), color = Color(0xFFC4CBE0))
                }
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp), state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(rows, key = { if (it is Row2.Day) "d:${it.key}" else (it as Row2.Bubble).m.id }) { row ->
                    when (row) {
                        is Row2.Day -> Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(row.label, Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 12.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, color = Color(0xFFD3D9EC))
                        }
                        is Row2.Bubble -> {
                            val m = row.m
                            val mine = m.sender_id == session.userId
                            val quoted = m.reply_to_id?.let { byId[it] }
                            val shape = RoundedCornerShape(
                                topStart = 18.dp, topEnd = 18.dp,
                                bottomStart = if (!mine && row.last) 4.dp else 18.dp,
                                bottomEnd = if (mine && row.last) 4.dp else 18.dp,
                            )
                            Row(Modifier.fillMaxWidth().padding(bottom = if (row.last) 5.dp else 0.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                                Box {
                                    Column(
                                        Modifier.widthIn(max = 300.dp).clip(shape)
                                            .then(if (mine) Modifier.background(OutBrush) else Modifier.background(InBubble))
                                            .combinedClickable(onClick = {}, onLongClick = { if (!m.deleted) menuFor = m })
                                            .padding(horizontal = 11.dp, vertical = 6.dp),
                                    ) {
                                        if (row.showSender) Text(state.users[m.sender_id]?.display_name ?: "?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        if (quoted != null) Row(Modifier.fillMaxWidth().padding(bottom = 3.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.22f)).padding(6.dp)) {
                                            Box(Modifier.size(width = 3.dp, height = 28.dp).background(Color(0xFFA9BBFF)))
                                            Text(
                                                (if (quoted.deleted) "…" else quoted.body.ifEmpty { stringResource(R.string.attachment) }).take(90),
                                                Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFFCDD5EE), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (m.deleted) Text(stringResource(R.string.msg_deleted), fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.7f))
                                        else {
                                            m.file?.let { f ->
                                                when (f.kind) {
                                                    "image" -> Box(Modifier.clickable { viewer = f }) { RemoteImage(engine, f) }
                                                    "video" -> VideoThumb(f) { viewer = f }
                                                    "voice" -> VoiceBubble(engine, f)
                                                    else -> FileRow(f) { pendingSave = f; saveLauncher.launch(f.name) }
                                                }
                                            }
                                            if (m.body.isNotEmpty()) Text(m.body, color = Color.White, fontSize = 16.sp)
                                            if (m.reactions.isNotEmpty()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                m.reactions.forEach { r ->
                                                    val minePick = r.user_ids.contains(session.userId)
                                                    Row(
                                                        Modifier.clip(RoundedCornerShape(12.dp)).background(if (minePick) Color.White.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.25f))
                                                            .clickable { engine.react(m.id, r.emoji) }.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) { Text(r.emoji, fontSize = 14.sp); Text(r.user_ids.size.toString(), color = Color.White, fontSize = 12.sp) }
                                                }
                                            }
                                        }
                                        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (m.edited_at != null && !m.deleted) Text(stringResource(R.string.edited), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                            Text(formatTime(m.created_at), fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f))
                                            if (mine && !m.deleted) Icon(if (m.status == "sent") Icons.Default.Done else Icons.Default.DoneAll, null, Modifier.size(16.dp), tint = if (m.status == "read") Color(0xFF9FE1FF) else Color.White.copy(alpha = 0.7f))
                                        }
                                    }
                                    DropdownMenu(expanded = menuFor?.id == m.id, onDismissRequest = { menuFor = null }) {
                                        Row(Modifier.padding(horizontal = 6.dp)) {
                                            listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { e -> Text(e, Modifier.clickable { engine.react(m.id, e); menuFor = null }.padding(6.dp), fontSize = 22.sp) }
                                        }
                                        if (!key.startsWith("g:") || engine.canManage(key)) DropdownMenuItem(text = { Text(stringResource(if (m.pinned) R.string.unpin else R.string.pin)) }, leadingIcon = { Icon(Icons.Default.PushPin, null) },
                                            onClick = { engine.pin(m.id, !m.pinned); menuFor = null })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.reply)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                                            onClick = { replyTo = m; editing = null; menuFor = null })
                                        if (m.body.isNotEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                            onClick = { clipboard.setText(AnnotatedString(m.body)); menuFor = null })
                                        if (mine && m.file == null) DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                                            onClick = { editing = m; replyTo = null; text = m.body; menuFor = null })
                                        if (mine || session.role != "member") DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                                            onClick = { engine.remove(m.id); menuFor = null })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- upload progress, reply bar, emoji ----
        state.uploads.forEach { u ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                Text(u.name + if (u.failed) "  ✕" else "", style = MaterialTheme.typography.bodySmall, color = if (u.failed) MaterialTheme.colorScheme.error else Color(0xFFC4CBE0), maxLines = 1)
                LinearProgressIndicator(progress = { u.progress }, Modifier.fillMaxWidth())
            }
        }
        val bar = editing ?: replyTo
        if (bar != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (editing != null) Icons.Default.Edit else Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(stringResource(if (editing != null) R.string.editing else R.string.replying_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(bar.body.ifEmpty { stringResource(R.string.attachment) }.take(60), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { replyTo = null; editing = null; text = "" }) { Icon(Icons.Default.Close, null) }
            }
        }
        if (emojiOpen) {
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)) {
                items(EMOJIS) { e -> Text(e, Modifier.clickable { text += e }.padding(8.dp), fontSize = 24.sp) }
            }
        }

        // ---- composer: rounded input + round mic/send button ----
        if (!engine.canPost(key)) Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Campaign, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.channel_read_only), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (recording) {
                        IconButton(onClick = { recorder.cancel(); recording = false }) { Icon(Icons.Default.Delete, stringResource(R.string.cancel), tint = MaterialTheme.colorScheme.error) }
                        Text("● " + fmtDuration(recSeconds), Modifier.weight(1f).padding(vertical = 14.dp), color = MaterialTheme.colorScheme.error)
                    } else {
                        IconButton(onClick = { emojiOpen = !emojiOpen }) { Icon(Icons.Default.EmojiEmotions, "Emoji", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            if (text.isEmpty()) Text(stringResource(R.string.message_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                            BasicTextField(
                                value = text, onValueChange = { text = it; if (it.isNotEmpty()) engine.typing(key) },
                                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp), maxLines = 5,
                                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            )
                        }
                        IconButton(onClick = { attachLauncher.launch("*/*") }) { Icon(Icons.Default.AttachFile, stringResource(R.string.attach), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            Surface(
                shape = CircleShape, color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(50.dp).clickable {
                    when {
                        recording -> { stopRecording(recorder, engine, key, scope) { toast(it) }; recording = false }
                        text.isNotBlank() -> submit()
                        else -> startRecording()
                    }
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when { recording -> Icons.Default.Stop; text.isNotBlank() -> Icons.AutoMirrored.Filled.Send; else -> Icons.Default.Mic },
                        stringResource(R.string.send), tint = Color.White,
                    )
                }
            }
        }
    }
    ChatDialogs(engine, key, viewer, confirm, { viewer = null }, { confirm = null }, onBack)
}

@Composable
private fun ChatDialogs(engine: ChatEngine, key: String, viewer: FileInfo?, confirm: String?, onViewerClose: () -> Unit, onConfirmClose: () -> Unit, onLeft: () -> Unit) {
    val scope = rememberCoroutineScope()
    viewer?.let { MediaDialog(engine, it, onViewerClose) }
    if (confirm != null) androidx.compose.material3.AlertDialog(
        onDismissRequest = onConfirmClose,
        text = { Text(stringResource(if (confirm == "delete") R.string.delete_group else R.string.confirm_leave_group)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onConfirmClose()
                scope.launch { try { if (confirm == "delete") engine.deleteGroup(key.drop(2)) else engine.leaveGroup(key.drop(2)); onLeft() } catch (_: Exception) {} }
            }) { Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onConfirmClose) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun VideoThumb(f: FileInfo, onOpen: () -> Unit) {
    Box(Modifier.size(width = 220.dp, height = 130.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp)) } }
        Text((f.duration_ms?.let { fmtDuration(it / 1000) + " · " } ?: "") + fmtSize(f.size), color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}

/** Full-screen viewer: pinch/double-tap zoom for pictures, a player with seek bar for videos. */
@Composable
private fun MediaDialog(engine: ChatEngine, f: FileInfo, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (f.kind == "video") {
                val token = engine.session?.token.orEmpty()
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            val mc = android.widget.MediaController(ctx)
                            mc.setAnchorView(this)
                            setMediaController(mc)
                            setVideoURI(android.net.Uri.parse(engine.streamUrl(f.id)), mapOf("Authorization" to "Bearer $token"))
                            setOnPreparedListener { it.start() }
                        }
                    },
                    onRelease = { it.stopPlayback() },
                )
            } else {
                val bmp by produceState<ImageBitmap?>(null, f.id) { value = engine.loadImage(f.id)?.asImageBitmap() }
                var scale by remember { mutableStateOf(1f) }
                var off by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                bmp?.let {
                    Image(
                        it, f.name,
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 6f); off = if (scale > 1f) off + pan else androidx.compose.ui.geometry.Offset.Zero } }
                            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; off = androidx.compose.ui.geometry.Offset.Zero }) }
                            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y },
                        contentScale = ContentScale.Fit,
                    )
                } ?: CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            IconButton(onClick = onClose, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) { Icon(Icons.Default.Close, null, tint = Color.White) }
        }
    }
}

private fun Modifier.height46() = this.heightIn(min = 46.dp)

private fun stopRecording(recorder: VoiceRecorder, engine: ChatEngine, key: String, scope: CoroutineScope, toast: (Int) -> Unit) {
    val f = recorder.stop() ?: return
    scope.launch {
        try {
            engine.sendFile(key, "voice.m4a", "voice", f.length(), recorder.lastMs) { f.inputStream() }
        } catch (e: ApiException) {
            toast(errMessage(e.code))
        } catch (e: Exception) {
            toast(R.string.err_unknown)
        } finally {
            f.delete()
        }
    }
}

@Composable
private fun RemoteImage(engine: ChatEngine, f: FileInfo) {
    val bmp by produceState<ImageBitmap?>(null, f.id) { value = engine.loadImage(f.id)?.asImageBitmap() }
    val b = bmp
    if (b != null) {
        Image(b, f.name, Modifier.widthIn(max = 260.dp).heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
    } else {
        Box(Modifier.size(width = 180.dp, height = 110.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
        }
    }
}

@Composable
private fun VoiceBubble(engine: ChatEngine, f: FileInfo) {
    val scope = rememberCoroutineScope()
    val playing by VoicePlayer.playing.collectAsState()
    val pos by VoicePlayer.positionMs.collectAsState()
    val dur by VoicePlayer.durationMs.collectAsState()
    val speed by VoicePlayer.speed.collectAsState()
    val current = playing == f.id
    val total = if (current && dur > 0) dur.toLong() else (f.duration_ms ?: 0L)
    var dragging by remember { mutableStateOf<Float?>(null) }
    Row(Modifier.widthIn(min = 230.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f), modifier = Modifier.size(42.dp).clickable {
            scope.launch { try { VoicePlayer.toggle(engine, f.id) } catch (_: Exception) {} }
        }) {
            Box(contentAlignment = Alignment.Center) { Icon(if (current && !VoicePlayer.isPaused()) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White) }
        }
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Slider(
                value = dragging ?: if (current && total > 0) (pos.toFloat() / total).coerceIn(0f, 1f) else 0f,
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { if (current) VoicePlayer.seekTo(it) }; dragging = null },
                modifier = Modifier.height(24.dp),
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text((if (current) fmtDuration(pos / 1000L) + " / " else "") + (if (total > 0) fmtDuration(total / 1000) else fmtSize(f.size)), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                Text(
                    (if (speed == 1f) "1" else speed.toString()) + "×", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.22f)).clickable { VoicePlayer.cycleSpeed() }.padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun FileRow(f: FileInfo, onDownload: () -> Unit) {
    Row(Modifier.clickable(onClick = onDownload).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f), modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.InsertDriveFile, null, tint = Color.White) }
        }
        Column(Modifier.widthIn(max = 180.dp)) {
            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(fmtSize(f.size), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        }
        Icon(Icons.Default.Download, stringResource(R.string.download), tint = Color.White)
    }
}
