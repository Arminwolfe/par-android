package com.par.chat.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.par.chat.R
import com.par.chat.data.ChatEngine
import com.par.chat.data.ChatState
import com.par.chat.data.Conn
import com.par.chat.data.IceConfig
import com.par.chat.data.Msg
import com.par.chat.data.Session
import com.par.chat.data.Store
import com.par.chat.data.convOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    session: Session,
    engine: ChatEngine,
    store: Store,
    onOpenChat: (String) -> Unit,
    onCall: (String, Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    val state by engine.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val unread = state.unread.values.sum()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            when (tab) {
                0 -> ChatsTab(session, state, engine, onOpenChat, onLeave)
                1 -> PeopleTab(session, state, onOpenChat, onCall)
                else -> SettingsTab(session, store, onLeave)
            }
        }
        BottomPill(tab, unread, { tab = it }, Modifier.align(Alignment.BottomCenter))
    }
}

/** Floating rounded navigation bar, like Telegram. */
@Composable
private fun BottomPill(selected: Int, unread: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp).fillMaxWidth(),
        shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, shadowElevation = 10.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            NavItem(Icons.Default.Chat, R.string.tab_chats, selected == 0, if (unread > 0) unread.toString() else null, Modifier.weight(1f)) { onSelect(0) }
            NavItem(Icons.Default.Person, R.string.tab_people, selected == 1, null, Modifier.weight(1f)) { onSelect(1) }
            NavItem(Icons.Default.Settings, R.string.tab_settings, selected == 2, null, Modifier.weight(1f)) { onSelect(2) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: Int, selected: Boolean, badge: String?, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier.clip(RoundedCornerShape(26.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(icon, null, tint = color)
            if (badge != null) {
                Text(
                    badge, color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 5.dp),
                )
            }
        }
        Text(stringResource(label), color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ConvRow(title: String, preview: String, time: String, online: Boolean?, unread: Int, typing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(title, online, 56.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (typing) stringResource(R.string.typing) else preview, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = if (typing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (unread > 0) {
                    Text(
                        unread.toString(), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private fun preview(m: Msg?, photo: String, voice: String, deleted: String): String = when {
    m == null -> ""
    m.deleted -> deleted
    m.file != null && m.body.isEmpty() -> when (m.file.kind) { "image" -> "🖼 $photo"; "voice" -> "🎤 $voice"; else -> "📎 ${m.file.name}" }
    else -> m.body
}

private enum class Filter { ALL, PEOPLE, GROUPS }

@Composable
private fun ChatsTab(session: Session, state: ChatState, engine: ChatEngine, onOpen: (String) -> Unit, onLeave: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Msg>?>(null) }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val photo = stringResource(R.string.pv_photo)
    val voice = stringResource(R.string.pv_voice)
    val deleted = stringResource(R.string.msg_deleted)
    val generalName = stringResource(R.string.general_group)

    LaunchedEffect(query) {
        if (query.isBlank()) { results = null; return@LaunchedEffect }
        delay(300)
        results = try { engine.search(query.trim()) } catch (e: Exception) { emptyList() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(session.networkName, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(when (state.conn) { Conn.ONLINE -> R.string.conn_online; Conn.CONNECTING -> R.string.conn_connecting; Conn.OFFLINE -> R.string.conn_offline }),
                        style = MaterialTheme.typography.bodySmall, color = if (state.conn == Conn.ONLINE) Good else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.new_group)) }, onClick = { menu = false; showGroupDialog = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.leave_network)) }, onClick = { menu = false; onLeave() })
                    }
                }
            }
            // search pill
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(28.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text(stringResource(R.string.search)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            // filter pill
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(Filter.ALL to R.string.tab_all, Filter.PEOPLE to R.string.tab_people, Filter.GROUPS to R.string.tab_groups).forEach { (f, label) ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                            .background(if (filter == f) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { filter = f }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(label), fontWeight = FontWeight.SemiBold, color = if (filter == f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val found = results
            if (found != null) {
                LazyColumn(Modifier.weight(1f)) {
                    items(found, key = { it.id }) { m ->
                        val key = convOf(m, session.userId)
                        ConvRow(convTitle(key, state, generalName), m.body.take(100), formatListTime(m.created_at), null, 0, false) { query = ""; onOpen(key) }
                    }
                }
            } else {
                // newest conversation first, like Telegram
                data class Item(val key: String, val title: String, val online: Boolean?, val last: Msg?)
                val groups = if (filter != Filter.PEOPLE) state.groups.map {
                    val k = "g:${it.id}"; Item(k, if (it.id == "general") generalName else it.name, null, state.msgs[k]?.lastOrNull())
                } else emptyList()
                val people = if (filter != Filter.GROUPS) state.users.values.filter { it.id != session.userId }.map {
                    val k = "u:${it.id}"; Item(k, it.display_name, it.online, state.msgs[k]?.lastOrNull())
                } else emptyList()
                val rows = (groups + people).sortedWith(compareByDescending<Item> { it.last?.seq ?: 0L }.thenByDescending { it.online == true }.thenBy { it.title })
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 130.dp)) {
                    items(rows, key = { it.key }) { r ->
                        ConvRow(
                            r.title, preview(r.last, photo, voice, deleted), r.last?.let { formatListTime(it.created_at) } ?: "", r.online,
                            state.unread[r.key] ?: 0, engine.typingNames(r.key).isNotEmpty(),
                        ) { onOpen(r.key) }
                    }
                    if (rows.isEmpty()) item { Text(stringResource(R.string.nobody_yet), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        FloatingActionButton(
            onClick = { showGroupDialog = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = androidx.compose.ui.graphics.Color.White,
            shape = CircleShape, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 104.dp).navigationBarsPadding(),
        ) { Icon(Icons.Default.Edit, stringResource(R.string.new_group)) }
    }
    if (showGroupDialog) NewGroupDialog(state, session, onDismiss = { showGroupDialog = false }) { name, ids ->
        showGroupDialog = false
        scope.launch { try { engine.createGroup(name, ids) } catch (_: Exception) {} }
    }
}

fun convTitle(key: String, state: ChatState, generalName: String): String =
    if (key.startsWith("g:")) state.groups.firstOrNull { it.id == key.drop(2) }?.let { if (it.id == "general") generalName else it.name } ?: "?"
    else state.users[key.drop(2)]?.display_name ?: "?"

@Composable
private fun NewGroupDialog(state: ChatState, session: Session, onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_group)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it.take(64) }, singleLine = true, label = { Text(stringResource(R.string.group_name)) })
                Spacer(Modifier.height(8.dp))
                state.users.values.filter { it.id != session.userId }.forEach { u ->
                    Row(Modifier.fillMaxWidth().clickable { selected = if (u.id in selected) selected - u.id else selected + u.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(u.id in selected, { selected = if (it) selected + u.id else selected - u.id })
                        Text(u.display_name)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), selected.toList()) }) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PeopleTab(session: Session, state: ChatState, onOpen: (String) -> Unit, onCall: (String, Boolean) -> Unit) {
    val users = state.users.values.filter { it.id != session.userId }.sortedByDescending { it.online }
    Text(stringResource(R.string.tab_people), fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 130.dp)) {
        items(users, key = { it.id }) { u ->
            Row(Modifier.fillMaxWidth().clickable { onOpen("u:${u.id}") }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(u.display_name, u.online, 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(u.display_name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(stringResource(if (u.online) R.string.online else R.string.offline) + if (u.role == "host") " · " + stringResource(R.string.role_host) else "", style = MaterialTheme.typography.bodySmall, color = if (u.online) Good else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (u.online && state.conn == Conn.ONLINE) {
                    IconButton(onClick = { onCall(u.id, false) }) { Icon(Icons.Default.Call, stringResource(R.string.call_audio), tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { onCall(u.id, true) }) { Icon(Icons.Default.Videocam, stringResource(R.string.call_video), tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        if (users.isEmpty()) item { Text(stringResource(R.string.nobody_yet), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SettingsTab(session: Session, store: Store, onLeave: () -> Unit) {
    var ice by remember { mutableStateOf(store.ice()) }
    var confirmLeave by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tab_settings), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(session.userName, null, 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(session.userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(session.networkName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(session.server, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(stringResource(R.string.language), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa")) }) { Text("فارسی") }
            OutlinedButton(onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }) { Text("English") }
        }
        Text(stringResource(R.string.calls_title), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.ice_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        fun save(c: IceConfig) { ice = c; store.saveIce(c) }
        OutlinedTextField(ice.stun, { save(ice.copy(stun = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.stun)) })
        OutlinedTextField(ice.turnUrl, { save(ice.copy(turnUrl = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.turn_url)) }, placeholder = { Text("turn:turn.example.com:3478") })
        OutlinedTextField(ice.turnUser, { save(ice.copy(turnUser = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.turn_user)) })
        OutlinedTextField(ice.turnPass, { save(ice.copy(turnPass = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.turn_pass)) }, visualTransformation = PasswordVisualTransformation())
        OutlinedButton(onClick = { save(ice.copy(turnUrl = "turn:openrelay.metered.ca:80", turnUser = "openrelayproject", turnPass = "openrelayproject")) }) { Text(stringResource(R.string.ice_free_turn)) }
        Text(stringResource(R.string.ice_free_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.plain_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.leave_network), color = MaterialTheme.colorScheme.error) }
        Text(stringResource(R.string.version_label) + " " + com.par.chat.BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(stringResource(R.string.leave_network)) },
        text = { Text(stringResource(R.string.leave_confirm)) },
        confirmButton = { TextButton(onClick = { confirmLeave = false; onLeave() }) { Text(stringResource(R.string.leave_network), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
