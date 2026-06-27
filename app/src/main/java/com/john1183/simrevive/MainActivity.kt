package com.john1183.simrevive

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    // Bug 4 fix: request POST_NOTIFICATIONS at runtime (Android 13+)
    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* granted or not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                SimReviveApp()
            }
        }
    }
}

fun getSimStateText(state: Int): Pair<String, Color> = when (state) {
    TelephonyManager.SIM_STATE_ABSENT       -> "No SIM detected in slot" to Color(0xFFE53935)
    TelephonyManager.SIM_STATE_READY        -> "SIM active and ready ✓" to Color(0xFF43A047)
    TelephonyManager.SIM_STATE_PIN_REQUIRED -> "SIM found — PIN required" to Color(0xFFFB8C00)
    TelephonyManager.SIM_STATE_PUK_REQUIRED -> "SIM found — PUK required" to Color(0xFFE53935)
    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "SIM network locked" to Color(0xFFE53935)
    TelephonyManager.SIM_STATE_NOT_READY    -> "SIM present but not ready yet" to Color(0xFFFB8C00)
    TelephonyManager.SIM_STATE_UNKNOWN      -> "SIM state unknown" to Color(0xFF757575)
    else -> "Checking..." to Color(0xFF757575)
}

// ── Attempt 1: broadcast the secret-code intent that triggers PhoneInfo directly
fun trySecretCodeBroadcast(ctx: Context): Boolean = runCatching {
    val i = Intent("android.provider.Telephony.SECRET_CODE")
    i.data = Uri.parse("android_secret_code://4636")
    i.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND or Intent.FLAG_RECEIVER_FOREGROUND)
    ctx.sendBroadcast(i)
    true
}.getOrDefault(false)

// ── Attempt 2: launch MTK engineer mode directly (Transsion/MediaTek devices)
fun tryMtkEngineerMode(ctx: Context): Boolean = runCatching {
    val pkgs = listOf(
        "com.mediatek.engineermode" to "com.mediatek.engineermode.EngineerMode",
        "com.mediatek.engineermode" to "com.mediatek.engineermode.MainActivity"
    )
    for ((pkg, cls) in pkgs) {
        if (ctx.packageManager.getLaunchIntentForPackage(pkg) != null) {
            val i = Intent().apply {
                component = ComponentName(pkg, cls)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)   // Bug 2 fix: required from non-Activity context
            }
            ctx.startActivity(i)
            return true
        }
    }
    false
}.getOrDefault(false)

// ── Attempt 3: launch Android's built-in Phone Testing (4636) via dialer
fun tryDialerCode(ctx: Context): Boolean = runCatching {
    val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:*%23*%234636%23*%23*"))
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)   // Bug 3 fix: required from non-Activity context
    ctx.startActivity(i)
    true
}.getOrDefault(false)

// ── Attempt 4: open SIM/mobile network settings as a last resort
fun openSimSettings(ctx: Context) = runCatching {
    val i = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(i)
}.onFailure {
    val i = Intent(Settings.ACTION_WIRELESS_SETTINGS)
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(i)
}

@Composable
fun SimReviveApp() {
    val ctx = LocalContext.current
    val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    var simState by remember { mutableIntStateOf(tm.simState) }
    var lastAction by remember { mutableStateOf("—") }
    var attemptLog by remember { mutableStateOf("") }

    val (simText, simColor) = getSimStateText(simState)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Header
        Text("SIM Revive", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Emergency SIM recovery tool", fontSize = 13.sp, color = Color(0xFF888888))

        Spacer(modifier = Modifier.height(4.dp))

        // ── SIM status pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(simColor.copy(alpha = 0.15f))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape)
                            .background(simColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        simText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = simColor
                    )
                }
                if (lastAction != "—") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Last: $lastAction", fontSize = 12.sp, color = Color(0xFF888888))
                }
            }
        }

        // ── Refresh
        OutlinedButton(
            onClick = {
                simState = tm.simState
                lastAction = "Refreshed status"
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFF444466))   // Bug 1 fix: BorderStroke has no .copy()
        ) {
            Text("Refresh SIM Status")
        }

        HorizontalDivider(color = Color(0xFF222233), thickness = 1.dp)

        Text(
            "Fix Attempts (try in order)",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFCCCCDD)
        )

        // ── Fix Button 1
        FixButton(
            number = "1",
            label = "Radio Engineer Mode",
            subtitle = "Best option — opens system radio toggle.\n" +
                       "Inside: tap 'Phone Information' → 'Turn Off Radio'\n" +
                       "Wait 3 seconds → 'Turn On Radio', then come back and Refresh.",
            color = Color(0xFF1565C0),
            onClick = {
                val ok = trySecretCodeBroadcast(ctx)
                lastAction = if (ok) "Sent broadcast to open engineer mode"
                             else "Broadcast sent (result unknown)"
                attemptLog += "• Secret code broadcast: ${if (ok) "sent" else "failed"}\n"
                if (!ok) {
                    val ok2 = tryMtkEngineerMode(ctx)
                    lastAction = if (ok2) "Opened MTK engineer mode" else "MTK mode not found"
                    attemptLog += "• MTK direct launch: ${if (ok2) "success" else "not available"}\n"
                }
                simState = tm.simState
            }
        )

        // ── Fix Button 2
        FixButton(
            number = "2",
            label = "Phone Test Mode (*#*#4636#*#*)",
            subtitle = "Backup if button 1 didn't open anything.\n" +
                       "Opens dialer — tap the green call button to execute.",
            color = Color(0xFF6A1B9A),
            onClick = {
                val ok = tryDialerCode(ctx)
                lastAction = if (ok) "Opened dialer with test code" else "Dialer failed"
                attemptLog += "• Dialer 4636: ${if (ok) "opened" else "failed"}\n"
                simState = tm.simState
            }
        )

        // ── Fix Button 3
        FixButton(
            number = "3",
            label = "SIM / Mobile Network Settings",
            subtitle = "Last resort — check slot assignment or toggle\n" +
                       "the SIM off and back on from settings.",
            color = Color(0xFF2E7D32),
            onClick = {
                openSimSettings(ctx)
                lastAction = "Opened SIM settings"
                attemptLog += "• SIM settings: opened\n"
            }
        )

        // ── Attempt log
        if (attemptLog.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A2E))
                    .padding(14.dp)
            ) {
                Column {
                    Text("Attempt Log", fontSize = 12.sp,
                         color = Color(0xFF666688), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(attemptLog.trimEnd(), fontSize = 11.sp, color = Color(0xFF9999BB),
                         lineHeight = 18.sp)
                }
            }
        }

        HorizontalDivider(color = Color(0xFF222233))

        Text(
            "After any toggle step:\nRefresh status above and wait ~10 seconds.\n" +
            "If SIM shows as PIN Required, enter your PIN in the dialer.",
            fontSize = 12.sp,
            color = Color(0xFF666677),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun FixButton(
    number: String,
    label: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(number, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f),
                     lineHeight = 17.sp)
            }
        }
    }
}
