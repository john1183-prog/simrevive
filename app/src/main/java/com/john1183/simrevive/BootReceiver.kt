package com.john1183.simrevive

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat

const val CHANNEL_ID = "sim_revive_channel"
const val NOTIF_ID   = 1001

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != "com.htc.intent.action.QUICKBOOT_POWERON") return

        // Bug 5 fix: Handler.postDelayed is killed before firing after boot.
        // AlarmManager survives even if the process is killed.
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            42,
            Intent(context, SimCheckReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Fire ~35 seconds after boot; inexact is fine — a few extra seconds doesn't matter
        am.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 35_000L,
            pi
        )
    }
}

// ── Fired by AlarmManager 35 seconds after boot ──────────────────────────────
class SimCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simOk = tm.simState == TelephonyManager.SIM_STATE_READY ||
                    tm.simState == TelephonyManager.SIM_STATE_PIN_REQUIRED

        if (!simOk) showSimAlertNotification(context)
    }
}

fun showSimAlertNotification(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel = NotificationChannel(
        CHANNEL_ID,
        "SIM Recovery Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts when SIM is not detected after boot"
        enableVibration(true)
    }
    nm.createNotificationChannel(channel)

    val openApp = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val fixNow = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, QuickFixReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("⚠ SIM Not Detected")
        .setContentText("Tap to open SIM Revive and fix it.")
        .setStyle(NotificationCompat.BigTextStyle()
            .bigText("SIM not detected 35s after boot. Tap 'Fix Now' for auto-fix, or open the app for step-by-step options."))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(openApp)
        .addAction(android.R.drawable.ic_media_play, "Fix Now", fixNow)
        .setAutoCancel(true)
        .build()

    nm.notify(NOTIF_ID, notification)
}
