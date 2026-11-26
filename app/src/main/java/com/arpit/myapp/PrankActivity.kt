package com.arpit.myapp

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fullscreen "you got pranked" screen, shown even over the lock screen. */
class PrankActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(0x80000 or 0x200000) // show when locked + turn screen on
        }
        val from = intent.getStringExtra("from") ?: "A buddy"
        val text = intent.getStringExtra("text")?.takeIf { it.isNotBlank() } ?: "GOT YOU! 😄"
        setContent { PrankScreen(from, text) { finish() } }
    }
}

@Composable
private fun PrankScreen(from: String, text: String, onClose: () -> Unit) {
    val t = rememberInfiniteTransition(label = "flash")
    val phase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "p"
    )
    val bg by animateColorAsState(
        if (phase > 0.5f) Color(0xFFD50000) else Color(0xFF6200EA),
        label = "bg"
    )
    Box(
        Modifier.fillMaxSize().background(bg).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("⚠️", fontSize = 80.sp)
            Text(
                text,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                "— from $from",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
            Button(onClick = onClose, modifier = Modifier.padding(top = 40.dp)) {
                Text("Close")
            }
        }
    }
}
