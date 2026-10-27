package com.par.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.par.chat.R
import com.par.chat.data.AddrTest
import com.par.chat.data.Api
import com.par.chat.data.ApiException
import com.par.chat.data.Invite
import com.par.chat.data.InvitePayload
import com.par.chat.data.Session
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Join-only screen: paste the invitation link, test it, pick a name, join. */
@Composable
fun JoinScreen(sharedText: String?, onJoined: (Session) -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var link by remember { mutableStateOf(sharedText.orEmpty()) }
    var manual by remember { mutableStateOf(false) }
    var manualAddr by remember { mutableStateOf("") }
    var manualCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var tests by remember { mutableStateOf<List<AddrTest>?>(null) }
    var testing by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var waiting by remember { mutableStateOf<Session?>(null) }
    var waitJob by remember { mutableStateOf<Job?>(null) }

    val invite: InvitePayload? = remember(link) { Invite.decode(link) }
    LaunchedEffect(link) { tests = null; errorRes = null }

    fun target(): Triple<List<String>, String?, String>? = when {
        !manual && invite != null -> Triple(invite.addrs, invite.network_id, invite.code)
        manual && Invite.validAddr(manualAddr) && Invite.normalizeCode(manualCode) != null ->
            Triple(listOf(manualAddr.trim()), null, Invite.normalizeCode(manualCode)!!)
        else -> null
    }

    fun fail(e: Exception) {
        errorRes = errMessage((e as? ApiException)?.code ?: "network")
        errorText = null
    }

    if (waiting != null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.waiting_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.waiting_sub), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = { waitJob?.cancel(); waiting = null }) { Text(stringResource(R.string.cancel)) }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(84.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.join_sub), color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!manual) {
            OutlinedTextField(
                value = link, onValueChange = { link = it },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                label = { Text(stringResource(R.string.invite_link)) },
                placeholder = { Text("SPINV1:…") },
                isError = link.isNotBlank() && invite == null,
                supportingText = { if (link.isNotBlank() && invite == null) Text(stringResource(R.string.err_invalid_link)) },
            )
            OutlinedButton(onClick = { clipboard.getText()?.text?.let { link = it } }) {
                Icon(Icons.Default.ContentPaste, null)
                Text(stringResource(R.string.paste), Modifier.padding(start = 8.dp))
            }
            if (invite != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(invite.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(invite.network_id, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            OutlinedTextField(manualAddr, { manualAddr = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.address)) }, placeholder = { Text("192.168.1.20:47600  |  [2001:db8::1]:47600") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(manualCode, { manualCode = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.code)) }, placeholder = { Text("XXXX-XXXX-XXXX-XXXX") })
        }
        TextButton(onClick = { manual = !manual; tests = null }) {
            Text(stringResource(if (manual) R.string.use_link else R.string.manual_entry))
        }

        if (!manual && invite?.pw == true || manual) {
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.password)) }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        }
        OutlinedTextField(name, { name = it.take(32) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.display_name)) })

        errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                modifier = Modifier.weight(1f), enabled = target() != null && !testing,
                onClick = {
                    val t = target() ?: return@OutlinedButton
                    testing = true; errorRes = null; tests = null
                    scope.launch { tests = Api.testAll(t.first, t.second); testing = false }
                },
            ) { Text(stringResource(if (testing) R.string.testing else R.string.test_connection)) }
            Button(
                modifier = Modifier.weight(1f),
                enabled = tests?.any { it.ok } == true && name.isNotBlank() && !joining,
                onClick = {
                    val t = target() ?: return@Button
                    val best = tests?.firstOrNull { it.ok } ?: return@Button
                    joining = true; errorRes = null
                    scope.launch {
                        try {
                            val (session, pending) = Api.join(best.addr, name.trim(), t.third, password)
                            if (pending) {
                                waiting = session
                                waitJob = scope.launch {
                                    while (true) {
                                        delay(3000)
                                        try {
                                            if (!Api.isPending(session)) { onJoined(session); break }
                                        } catch (e: ApiException) {
                                            if (e.code == "unauthorized") { waiting = null; errorRes = R.string.rejected_msg; break }
                                        }
                                    }
                                }
                            } else onJoined(session)
                        } catch (e: Exception) {
                            fail(e)
                        }
                        joining = false
                    }
                },
            ) { Text(stringResource(if (joining) R.string.joining else R.string.join)) }
        }

        tests?.let { list ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (t.ok) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (t.ok) Good else Bad)
                            Column {
                                Text(t.addr, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (t.ok) "${t.latencyMs} ms" else stringResource(errMessage(t.errorCode ?: "network")),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (list.none { it.ok }) Text(stringResource(R.string.none_reachable), color = MaterialTheme.colorScheme.error)
                    else Text(stringResource(R.string.test_ok_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(stringResource(R.string.plain_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
