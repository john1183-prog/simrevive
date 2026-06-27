package com.john1183.simrevive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat

const val CHANNEL_ID = "sim_revive_channel"
const val NOTIF_ID   = 1001

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        // Delay 35 seconds — gives the system time to try (and fail) SIM detection first.
        // If SIM comes up on its own during that window, no notification fires.
        Handler(Looper.getMainLooper()).postDelayed({
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val simOk = tm.simState == TelephonyManager.SIM_STATE_READY ||
                        tm.simState == TelephonyManager.SIM_STATE_PIN_REQUIRED

            if (!simOk) {
                // SIM still not detected after 35s — fire the alert notification
                showSimAlertNotification(context)
            }
        }, 35_000L)
    }
}

fun showSimAlertNotification(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Create channel (Android 8+)
    val channel = NotificationChannel(
        CHANNEL_ID,
        "SIM Recovery Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts when SIM is not detected after boot"
        enableVibration(true)
    }
    nm.createNotificationChannel(channel)

    // Intent to open the app when notification is tapped
    val openApp = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Quick-fix action button — fires the secret code broadcast directly
    val fixNow = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, QuickFixReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("⚠ SIM Not Detected")
        .setContentText("Tap to open SIM Revive and fix it.")
        .setStyle(NotificationCompat.BigTextStyle()
            .bigText("Your SIM was not detected after boot. Tap 'Fix Now' to try an auto-fix, or open the app for step-by-step options."))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(openApp)
        .addAction(android.R.drawable.ic_media_play, "Fix Now", fixNow)
        .setAutoCancel(true)
        .build()

    nm.notify(NOTIF_ID, notification)
}
