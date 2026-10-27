package com.par.chat

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps the process (and so the WebSocket connection) alive in the background,
 * the way messengers do without push. While a call is active it also declares the microphone/camera types
 * so Android keeps audio and video running if the app is minimised.
 */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val call = intent?.getIntExtra(EXTRA_CALL, 0) ?: 0
        val app = application as ParApp
        val notification = Notifier.connection(this, app.session.value?.networkName ?: "", call > 0)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                var types = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
                if (call >= 1) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (call == 2) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                ServiceCompat.startForeground(this, 1, notification, types)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            // Android refuses to start a foreground service from the background in some situations; the app still works while open.
            stopSelf()
        }
        return START_STICKY
    }

    companion object {
        private const val EXTRA_CALL = "call"

        /** `call`: 0 = just the connection, 1 = voice call, 2 = video call. */
        fun setCall(ctx: Context, call: Int) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java).putExtra(EXTRA_CALL, call))
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, KeepAliveService::class.java))
        }
    }
}
