package com.john1183.simrevive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Triggered from the "Fix Now" notification action button.
 * Fires all fix attempts in sequence without needing the user to open the app.
 */
class QuickFixReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Attempt 1: secret code broadcast (often opens engineer mode silently)
        val ok1 = trySecretCodeBroadcast(context)

        // Attempt 2: MTK direct launch
        val ok2 = if (!ok1) tryMtkEngineerMode(context) else false

        // Attempt 3: dialer fallback — needs user to press call
        if (!ok1 && !ok2) {
            runCatching {
                val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:*%23*%234636%23*%23*"))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }
        }

        Toast.makeText(
            context,
            if (ok1 || ok2) "Radio reset triggered — wait 10s then check SIM"
            else "Opened phone test mode — toggle radio off then on",
            Toast.LENGTH_LONG
        ).show()
    }
}
