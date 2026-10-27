package com.par.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.par.chat.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

fun formatTime(iso: String): String = try {
    timeFmt.format(Instant.parse(iso))
} catch (e: Exception) {
    ""
}

/** Time for today, otherwise a short date (conversation list). */
fun formatListTime(iso: String): String = try {
    val inst = Instant.parse(iso)
    val zone = ZoneId.systemDefault()
    val d = inst.atZone(zone).toLocalDate()
    if (d == java.time.LocalDate.now(zone)) timeFmt.format(inst)
    else DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()).withZone(zone).format(inst)
} catch (e: Exception) {
    ""
}

fun fmtSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
}

fun fmtDuration(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

/** Maps a host or client error code to a localised message. */
fun errMessage(code: String): Int = when (code) {
    "network", "timeout" -> R.string.err_network
    "invalid_invitation" -> R.string.err_invalid_invitation
    "invalid_password" -> R.string.err_invalid_password
    "name_taken" -> R.string.err_name_taken
    "rate_limited" -> R.string.err_rate_limited
    "network_full" -> R.string.err_network_full
    "pending_approval" -> R.string.err_pending_approval
    "invalid_name" -> R.string.err_invalid_name
    "file_too_large" -> R.string.err_file_too_large
    "blocked_file_type" -> R.string.err_blocked_file_type
    "storage_full" -> R.string.err_storage_full
    "invalid_voice" -> R.string.err_invalid_voice
    "empty_file" -> R.string.err_empty_file
    "invalid_file" -> R.string.err_invalid_file
    "not_allowed" -> R.string.err_not_allowed
    "too_many_connections" -> R.string.err_too_many
    "not_par" -> R.string.err_not_par
    "mismatch" -> R.string.err_mismatch
    else -> R.string.err_unknown
}

fun callEndMessage(reason: String): Int = when (reason) {
    "ended_remote" -> R.string.call_ended_remote
    "ended_local" -> R.string.call_ended
    "rejected" -> R.string.call_rejected
    "busy" -> R.string.call_busy
    "unavailable" -> R.string.call_unavailable
    "timeout" -> R.string.call_timeout
    "connection_failed" -> R.string.call_failed
    else -> R.string.call_media_error
}

@Composable
fun Avatar(name: String, online: Boolean? = null, size: Dp = 42.dp) {
    val hue = (name.fold(0) { h, c -> (h * 31 + c.code) % 360 }).toFloat()
    val c1 = Color.hsv(hue, 0.65f, 0.75f)
    val c2 = Color.hsv((hue + 40f) % 360f, 0.7f, 0.55f)
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(c1, c2))),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.trim().take(1).uppercase().ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
        if (online != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size / 3.4f)
                    .clip(CircleShape).background(if (online) Good else Muted)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
            )
        }
    }
}
