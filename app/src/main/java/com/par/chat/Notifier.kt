package com.par.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Notification channels and builders: connection (foreground service), messages and incoming calls. */
object Notifier {
    const val CH_CONN = "par_conn"
    const val CH_MSG = "par_msg"
    const val CH_CALL = "par_call"
    const val EXTRA_CONV = "conv"
    private const val CALL_ID = 4242

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_CONN, ctx.getString(R.string.notif_channel_conn), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_MSG, ctx.getString(R.string.notif_channel_msg), NotificationManager.IMPORTANCE_HIGH))
        val call = NotificationChannel(CH_CALL, ctx.getString(R.string.notif_channel_call), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            enableVibration(true)
        }
        nm.createNotificationChannel(call)
    }

    private fun openApp(ctx: Context, conv: String?, requestCode: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (conv != null) putExtra(EXTRA_CONV, conv)
        }
        return PendingIntent.getActivity(ctx, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** The permanent notification of the background connection. */
    fun connection(ctx: Context, networkName: String, inCall: Boolean): Notification {
        ensureChannels(ctx)
        return NotificationCompat.Builder(ctx, CH_CONN)
            .setSmallIcon(R.drawable.ic_stat_par)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(ctx.getString(if (inCall) R.string.notif_in_call else R.string.notif_connected, networkName))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp(ctx, null, 1))
            .build()
    }

    fun message(ctx: Context, title: String, text: String, convKey: String) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_MSG)
            .setSmallIcon(R.drawable.ic_stat_par)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, convKey, convKey.hashCode()))
            .build()
        try { nm.notify(convKey.hashCode(), n) } catch (_: SecurityException) {}
    }

    /** High-priority call notification that can open the call screen over the lock screen. */
    fun incomingCall(ctx: Context, name: String, video: Boolean) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels(ctx)
        val open = openApp(ctx, null, 2)
        val n = NotificationCompat.Builder(ctx, CH_CALL)
            .setSmallIcon(R.drawable.ic_stat_par)
            .setContentTitle(name)
            .setContentText(ctx.getString(if (video) R.string.incoming_video else R.string.incoming_audio))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .build()
        try { nm.notify(CALL_ID, n) } catch (_: SecurityException) {}
    }

    fun cancelCall(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(CALL_ID)
    }
}
