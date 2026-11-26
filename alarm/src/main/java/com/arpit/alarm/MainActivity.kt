package com.arpit.alarm

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AlarmApp() }
    }
}

@Composable
private fun AlarmApp() {
    val ctx = LocalContext.current
    val running by AlarmState.running.collectAsState()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        beginAlarm(ctx)
    }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Whatever the user chose, start the alarm; locking just works better if they said yes
        startAfterPermissions(ctx, notifLauncher::launch)
    }

    fun onStart() {
        if (!isAdminActive(ctx)) {
            adminLauncher.launch(adminIntent(ctx))
        } else {
            startAfterPermissions(ctx, notifLauncher::launch)
        }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (!running) {
                Text("⏰", fontSize = 72.sp)
                Text(
                    "Loud Alarm",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
                )
                BigButton("START", Color(0xFF00C853)) { onStart() }
                Text(
                    "Plays the alarm at full volume and locks the screen. Unlock to stop it.",
                    color = Color(0xFFB0B8C8),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 28.dp),
                )
            } else {
                PulsingLabel()
                BigButton("STOP", Color(0xFFD50000)) { AlarmService.stop(ctx) }
            }
        }
    }
}

@Composable
private fun PulsingLabel() {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    val c by animateColorAsState(Color(0xFFFF5252).copy(alpha = a), label = "c")
    Text("🔔", fontSize = 72.sp)
    Text(
        "ALARM ON",
        color = c,
        fontSize = 28.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
    )
}

@Composable
private fun BigButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier.size(180.dp),
    ) {
        Text(label, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
    }
}

// ---------- helpers ----------

private fun startAfterPermissions(ctx: Context, requestNotif: (String) -> Unit) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        requestNotif(Manifest.permission.POST_NOTIFICATIONS)
        return
    }
    beginAlarm(ctx)
}

private fun beginAlarm(ctx: Context) {
    AlarmService.start(ctx)
    if (isAdminActive(ctx)) {
        try {
            ctx.getSystemService(DevicePolicyManager::class.java)?.lockNow()
        } catch (_: Exception) { }
    }
}

private fun adminComponent(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

private fun isAdminActive(ctx: Context): Boolean =
    ctx.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(adminComponent(ctx)) == true

private fun adminIntent(ctx: Context): Intent =
    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(ctx))
        putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Allow Loud Alarm to lock the screen when you tap Start."
        )
    }
