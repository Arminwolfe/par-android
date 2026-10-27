package com.par.chat.call

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import com.par.chat.data.ChatEngine
import com.par.chat.data.EngineEvent
import com.par.chat.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID

enum class CallState { IDLE, CALLING, RINGING_IN, CONNECTING, ACTIVE, RECONNECTING }

data class CallUi(
    val state: CallState = CallState.IDLE,
    val peerId: String = "",
    val video: Boolean = false,
    val muted: Boolean = false,
    val camOff: Boolean = false,
    val speaker: Boolean = false,
    val startedAt: Long = 0,
)

private open class SdpAdapter : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

/**
 * One-to-one audio/video calls with WebRTC. Signalling goes through the host's WebSocket using the same
 * messages as the desktop app, so Android and Windows can call each other. Media is peer to peer (STUN)
 * or relayed by a TURN server you configure.
 */
class CallManager(private val app: Application, private val engine: ChatEngine, private val store: Store) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _ui = MutableStateFlow(CallUi())
    val ui: StateFlow<CallUi> = _ui
    val localVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo = MutableStateFlow<VideoTrack?>(null)
    /** One-shot message for the UI (reason code such as "rejected", "busy", "ended_remote"). */
    val ended = MutableStateFlow<String?>(null)

    val eglBase: EglBase by lazy { EglBase.create() }
    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private var pc: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
    private var videoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var callId = ""
    private var isCaller = false
    private var restarts = 0
    private val pendingIce = mutableListOf<IceCandidate>()
    private var ringTimer: Job? = null
    private var ringtone: Ringtone? = null
    private val audio get() = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        scope.launch {
            engine.events.collect { e -> if (e is EngineEvent.Call) onSignal(e.data) }
        }
    }

    val busy: Boolean get() = _ui.value.state != CallState.IDLE

    // ---- public actions ------------------------------------------------------------

    /** Microphone (and camera for video) permission must already be granted. */
    fun start(peerId: String, video: Boolean) {
        if (busy) return
        reset()
        callId = UUID.randomUUID().toString().take(20)
        isCaller = true
        _ui.value = CallUi(CallState.CALLING, peerId, video)
        if (!engine.sendCall("invite", peerId, callId, video)) {
            finish("unavailable", false)
            return
        }
        ringTimer = scope.launch { delay(45_000); finish("timeout", true) }
    }

    fun accept() {
        val u = _ui.value
        if (u.state != CallState.RINGING_IN) return
        stopRing()
        com.par.chat.Notifier.cancelCall(app)
        ringTimer?.cancel()
        _ui.update { it.copy(state = CallState.CONNECTING) }
        try {
            createPeer(u.video)
        } catch (e: Exception) {
            engine.sendCall("reject", u.peerId, callId, u.video)
            finish("media_error", false)
            return
        }
        engine.sendCall("accept", u.peerId, callId, u.video)
    }

    fun reject() {
        val u = _ui.value
        if (u.state != CallState.RINGING_IN) return
        engine.sendCall("reject", u.peerId, callId, u.video)
        finish("ended_local", false)
    }

    fun end() {
        val u = _ui.value
        if (!busy) return
        engine.sendCall("end", u.peerId, callId, u.video)
        finish("ended_local", false)
    }

    fun toggleMute() {
        val m = !_ui.value.muted
        audioTrack?.setEnabled(!m)
        _ui.update { it.copy(muted = m) }
    }

    fun toggleCamera() {
        val off = !_ui.value.camOff
        videoTrack?.setEnabled(!off)
        _ui.update { it.copy(camOff = off) }
    }

    fun toggleSpeaker() {
        val on = !_ui.value.speaker
        audio.isSpeakerphoneOn = on
        _ui.update { it.copy(speaker = on) }
    }

    fun switchCamera() {
        capturer?.switchCamera(null)
    }

    // ---- signalling ------------------------------------------------------------------

    private fun onSignal(o: JsonObject) {
        val action = o["action"]?.jsonPrimitive?.contentOrNull ?: return
        val from = o["from"]?.jsonPrimitive?.contentOrNull ?: return
        val id = o["call_id"]?.jsonPrimitive?.contentOrNull ?: return
        val video = o["video"]?.jsonPrimitive?.contentOrNull == "true"
        if (action == "invite") {
            if (busy) {
                engine.sendCall("busy", from, id, video)
                return
            }
            reset()
            callId = id
            isCaller = false
            _ui.value = CallUi(CallState.RINGING_IN, from, video)
            startRing()
            if (!(app as com.par.chat.ParApp).inForeground) {
                com.par.chat.Notifier.incomingCall(app, engine.state.value.users[from]?.display_name ?: "?", video)
            }
            ringTimer = scope.launch { delay(45_000); reject() }
            return
        }
        val u = _ui.value
        if (id != callId || from != u.peerId) return // stale or foreign call
        when (action) {
            "accept" -> if (isCaller && u.state == CallState.CALLING) {
                ringTimer?.cancel()
                _ui.update { it.copy(state = CallState.CONNECTING) }
                try {
                    createPeer(u.video)
                    createOffer(false)
                } catch (e: Exception) {
                    finish("media_error", true)
                }
            }
            "reject" -> finish("rejected", false)
            "busy" -> finish("busy", false)
            "unavailable" -> finish("unavailable", false)
            "end" -> finish("ended_remote", false)
            "sdp" -> {
                val d = o["data"]?.jsonObject ?: return
                val type = d["type"]?.jsonPrimitive?.contentOrNull ?: return
                val sdp = d["sdp"]?.jsonPrimitive?.contentOrNull ?: return
                val peer = pc ?: return
                peer.setRemoteDescription(object : SdpAdapter() {
                    override fun onSetSuccess() {
                        drainIce()
                        if (type == "offer") {
                            peer.createAnswer(object : SdpAdapter() {
                                override fun onCreateSuccess(p0: SessionDescription?) {
                                    val desc = p0 ?: return
                                    peer.setLocalDescription(SdpAdapter(), desc)
                                    sendSdp(desc)
                                }
                            }, MediaConstraints())
                        }
                    }

                    override fun onSetFailure(p0: String?) = finish("connection_failed", true)
                }, SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp))
            }
            "ice" -> {
                val d = o["data"]?.jsonObject ?: return
                val cand = IceCandidate(
                    d["sdpMid"]?.jsonPrimitive?.contentOrNull,
                    d["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    d["candidate"]?.jsonPrimitive?.contentOrNull ?: return,
                )
                val peer = pc
                if (peer != null && peer.remoteDescription != null) peer.addIceCandidate(cand) else pendingIce.add(cand)
            }
        }
    }

    private fun drainIce() {
        val peer = pc ?: return
        pendingIce.toList().also { pendingIce.clear() }.forEach { peer.addIceCandidate(it) }
    }

    private fun sendSdp(desc: SessionDescription) {
        engine.sendCall("sdp", _ui.value.peerId, callId, _ui.value.video, buildJsonObject {
            put("type", desc.type.canonicalForm())
            put("sdp", desc.description)
        })
    }

    private fun createOffer(iceRestart: Boolean) {
        val peer = pc ?: return
        val constraints = MediaConstraints().apply {
            if (iceRestart) mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        peer.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                val desc = p0 ?: return
                peer.setLocalDescription(SdpAdapter(), desc)
                sendSdp(desc)
            }

            override fun onCreateFailure(p0: String?) = finish("connection_failed", true)
        }, constraints)
    }

    // ---- WebRTC plumbing --------------------------------------------------------------

    /** A TURN server set in this app's Settings wins; otherwise the host's servers are used automatically. */
    private fun iceServers(): List<PeerConnection.IceServer> {
        val local = store.ice()
        val host = engine.hostIce
        val customStun = local.stun.isNotBlank() && local.stun != "stun:stun.l.google.com:19302"
        val stun = if (customStun || host == null || host.stun.isBlank()) local.stun else host.stun
        val turnUrl = if (local.turnUrl.isNotBlank() || host == null) local.turnUrl else host.turn_url
        val turnUser = if (local.turnUrl.isNotBlank() || host == null) local.turnUser else host.turn_user
        val turnPass = if (local.turnUrl.isNotBlank() || host == null) local.turnPass else host.turn_pass
        val list = mutableListOf<PeerConnection.IceServer>()
        if (stun.isNotBlank()) list.add(PeerConnection.IceServer.builder(stun.trim()).createIceServer())
        if (turnUrl.isNotBlank()) list.add(PeerConnection.IceServer.builder(turnUrl.trim()).setUsername(turnUser).setPassword(turnPass).createIceServer())
        return list
    }

    private fun createPeer(video: Boolean) {
        com.par.chat.KeepAliveService.setCall(app, if (video) 2 else 1)
        val audioManager = audio
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = video
        _ui.update { it.copy(speaker = video) }

        val config = PeerConnection.RTCConfiguration(iceServers()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        val peer = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
            override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}

            override fun onIceCandidate(c: IceCandidate?) {
                val cand = c ?: return
                engine.sendCall("ice", _ui.value.peerId, callId, _ui.value.video, buildJsonObject {
                    put("candidate", cand.sdp)
                    put("sdpMid", cand.sdpMid ?: "")
                    put("sdpMLineIndex", cand.sdpMLineIndex)
                })
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val t = transceiver?.receiver?.track()
                if (t is VideoTrack) remoteVideo.value = t
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                scope.launch { onConnectionState(state) }
            }
        }) ?: throw IllegalStateException("peer connection failed")
        pc = peer

        val aSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("par-a", aSource).also { peer.addTrack(it, listOf("par")) }
        if (video) startCamera(peer)
    }

    private fun startCamera(peer: PeerConnection) {
        val enumerator = Camera2Enumerator(app)
        val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull()
            ?: throw IllegalStateException("no camera")
        val cap = enumerator.createCapturer(name, null) ?: throw IllegalStateException("camera unavailable")
        capturer = cap
        val helper = SurfaceTextureHelper.create("par-capture", eglBase.eglBaseContext)
        textureHelper = helper
        val source = factory.createVideoSource(false)
        videoSource = source
        cap.initialize(helper, app, source.capturerObserver)
        cap.startCapture(1280, 720, 30)
        val track = factory.createVideoTrack("par-v", source)
        videoTrack = track
        peer.addTrack(track, listOf("par"))
        localVideo.value = track
    }

    private fun onConnectionState(state: PeerConnection.PeerConnectionState?) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                restarts = 0
                _ui.update { it.copy(state = CallState.ACTIVE, startedAt = if (it.startedAt == 0L) System.currentTimeMillis() else it.startedAt) }
            }
            PeerConnection.PeerConnectionState.DISCONNECTED -> if (_ui.value.state == CallState.ACTIVE) _ui.update { it.copy(state = CallState.RECONNECTING) }
            PeerConnection.PeerConnectionState.FAILED -> {
                // Only the caller renegotiates (ICE restart); after 3 tries the call is given up.
                if (isCaller && restarts < 3) {
                    restarts++
                    _ui.update { it.copy(state = CallState.RECONNECTING) }
                    pc?.restartIce()
                    createOffer(true)
                } else if (isCaller) finish("connection_failed", true)
                else _ui.update { it.copy(state = CallState.RECONNECTING) }
            }
            else -> {}
        }
    }

    /** Ends everything locally. `notify` also tells the other side (used for local failures). */
    private fun finish(reason: String, notify: Boolean) {
        val u = _ui.value
        if (notify && u.peerId.isNotEmpty()) engine.sendCall("end", u.peerId, callId, u.video)
        reset()
        ended.value = reason
    }

    private fun reset() {
        ringTimer?.cancel()
        stopRing()
        try { capturer?.stopCapture() } catch (_: Exception) {}
        capturer?.dispose()
        capturer = null
        textureHelper?.dispose()
        textureHelper = null
        videoTrack?.dispose(); videoTrack = null
        videoSource?.dispose(); videoSource = null
        audioTrack?.dispose(); audioTrack = null
        pc?.close(); pc = null
        localVideo.value = null
        remoteVideo.value = null
        pendingIce.clear()
        restarts = 0
        audio.mode = AudioManager.MODE_NORMAL
        audio.isSpeakerphoneOn = false
        com.par.chat.Notifier.cancelCall(app)
        if (engine.session != null) com.par.chat.KeepAliveService.setCall(app, 0)
        _ui.value = CallUi()
    }

    private fun startRing() {
        try {
            ringtone = RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.also { it.play() }
        } catch (_: Exception) {
        }
    }

    private fun stopRing() {
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
    }
}
