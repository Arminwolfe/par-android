package com.par.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.par.chat.R
import com.par.chat.call.CallManager
import com.par.chat.call.CallState
import com.par.chat.data.ChatEngine
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
private fun VideoView(track: VideoTrack?, egl: EglBase, mirror: Boolean, overlay: Boolean, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(ctx).apply {
            init(egl.eglBaseContext, null)
            setEnableHardwareScaler(true)
            setMirror(mirror)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            if (overlay) setZOrderMediaOverlay(true)
        }
    }
    DisposableEffect(track) {
        track?.addSink(renderer)
        onDispose { track?.removeSink(renderer) }
    }
    DisposableEffect(Unit) { onDispose { renderer.release() } }
    AndroidView(factory = { renderer }, modifier = modifier)
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, container: Color, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick, modifier = Modifier.size(58.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = Color.White),
    ) { Icon(icon, desc, Modifier.size(26.dp)) }
}

@Composable
fun CallScreen(calls: CallManager, engine: ChatEngine, onAccept: () -> Unit) {
    val ui by calls.ui.collectAsState()
    val local by calls.localVideo.collectAsState()
    val remote by calls.remoteVideo.collectAsState()
    val state by engine.state.collectAsState()
    val name = state.users[ui.peerId]?.display_name ?: "?"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.state) { while (ui.state == CallState.ACTIVE) { now = System.currentTimeMillis(); delay(1000) } }

    Box(Modifier.fillMaxSize().background(Color(0xFF050A18))) {
        val showRemote = ui.video && remote != null
        if (showRemote) VideoView(remote, calls.eglBase, mirror = false, overlay = false, modifier = Modifier.fillMaxSize())
        else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(name, null, 120.dp)
        }
        if (ui.video && local != null && !ui.camOff) {
            VideoView(local, calls.eglBase, mirror = true, overlay = true, modifier = Modifier.statusBarsPadding().padding(12.dp).size(width = 110.dp, height = 160.dp).align(Alignment.TopEnd).clip(RoundedCornerShape(14.dp)))
        }
        Column(Modifier.statusBarsPadding().align(Alignment.TopCenter).padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                when (ui.state) {
                    CallState.CALLING -> stringResource(R.string.calling)
                    CallState.RINGING_IN -> stringResource(if (ui.video) R.string.incoming_video else R.string.incoming_audio)
                    CallState.CONNECTING -> stringResource(R.string.connecting)
                    CallState.RECONNECTING -> stringResource(R.string.reconnecting)
                    CallState.ACTIVE -> fmtDuration(((now - ui.startedAt) / 1000).coerceAtLeast(0))
                    CallState.IDLE -> ""
                },
                color = Color(0xFFB8C7E6),
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp).fillMaxWidth()) {
            if (ui.state == CallState.RINGING_IN) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(Icons.Default.CallEnd, stringResource(R.string.reject), Color(0xFFE53950)) { calls.reject() }
                    RoundButton(if (ui.video) Icons.Default.Videocam else Icons.Default.Call, stringResource(R.string.accept), Color(0xFF1FB57A), onAccept)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(if (ui.muted) Icons.Default.MicOff else Icons.Default.Mic, stringResource(R.string.mute), if (ui.muted) Color(0xFF5A6A8F) else Color(0x33FFFFFF)) { calls.toggleMute() }
                    RoundButton(if (ui.speaker) Icons.Default.VolumeUp else Icons.Default.VolumeOff, stringResource(R.string.speaker), if (ui.speaker) Color(0xFF2575FC) else Color(0x33FFFFFF)) { calls.toggleSpeaker() }
                    if (ui.video) {
                        RoundButton(if (ui.camOff) Icons.Default.VideocamOff else Icons.Default.Videocam, stringResource(R.string.camera), if (ui.camOff) Color(0xFF5A6A8F) else Color(0x33FFFFFF)) { calls.toggleCamera() }
                        RoundButton(Icons.Default.Cameraswitch, stringResource(R.string.switch_camera), Color(0x33FFFFFF)) { calls.switchCamera() }
                    }
                    RoundButton(Icons.Default.CallEnd, stringResource(R.string.end_call), Color(0xFFE53950)) { calls.end() }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
