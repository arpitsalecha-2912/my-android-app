package com.arpit.myapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.arpit.myapp.net.PrankBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Keeps a UDP listener alive so buddies can reach this phone, and runs the prank effects. */
class PrankService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: DatagramSocket? = null
    private var mcLock: WifiManager.MulticastLock? = null
    private var ringtone: Ringtone? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        acquireLock()
        startSocket()
        PrankBus.setRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun acquireLock() {
        try {
            val wm = applicationContext.getSystemService(WifiManager::class.java)
            mcLock = wm?.createMulticastLock("prank")?.apply { setReferenceCounted(false); acquire() }
        } catch (_: Exception) { }
    }

    private fun startSocket() {
        // Receiver loop
        scope.launch {
            try {
                val s = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(java.net.InetSocketAddress(PrankBus.PORT))
                }
                socket = s
                val buf = ByteArray(2048)
                while (isActive) {
                    val pkt = DatagramPacket(buf, buf.size)
                    s.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length)
                    handle(msg, pkt.address.hostAddress ?: "")
                }
            } catch (_: Exception) { }
        }
        // Heartbeat so buddies discover us
        scope.launch {
            while (isActive) {
                try {
                    DatagramSocket().use { s ->
                        s.broadcast = true
                        val data = PrankBus.helloPacket()
                        s.send(DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), PrankBus.PORT))
                    }
                } catch (_: Exception) { }
                PrankBus.prune()
                delay(3000)
            }
        }
    }

    private fun handle(msg: String, fromIp: String) {
        val parts = msg.split(PrankBus.SEP)
        when (parts.getOrNull(0)) {
            "HELLO" -> if (parts.size >= 3) PrankBus.onHello(parts[1], parts[2], fromIp)
            "CMD" -> {
                val type = parts.getOrNull(1) ?: return
                val from = parts.getOrNull(2) ?: "A buddy"
                val text = parts.getOrNull(3) ?: ""
                when (type) {
                    PrankBus.CMD_RING -> ring()
                    PrankBus.CMD_BUZZ -> buzz()
                    PrankBus.CMD_MSG -> showMessage(from, text)
                }
            }
        }
    }

    // ---------- effects ----------

    private fun ring() {
        try {
            val am = getSystemService(AudioManager::class.java)
            am?.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
            )
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone?.stop()
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
            buzz()
            scope.launch { delay(6000); try { ringtone?.stop() } catch (_: Exception) {} }
        } catch (_: Exception) { }
    }

    private fun buzz() {
        try {
            val pattern = longArrayOf(0, 400, 200, 400, 200, 600)
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Vibrator::class.java)
                @Suppress("DEPRECATION")
                v?.vibrate(pattern, -1)
            }
        } catch (_: Exception) { }
    }

    private fun showMessage(from: String, text: String) {
        PrankBus.postIncoming(from, text)
        buzz()
        val intent = Intent(this, PrankActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("from", from)
            putExtra("text", text)
        }
        val pi = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("😱 $from")
            .setContentText(text.ifBlank { "pranked you!" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
        try {
            getSystemService(NotificationManager::class.java)?.notify(ALERT_ID, n)
        } catch (_: Exception) { }
        // If the app is in the foreground this launches immediately too
        try { startActivity(intent) } catch (_: Exception) { }
    }

    // ---------- notifications ----------

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm?.createNotificationChannel(
                NotificationChannel(SVC_CHANNEL, "Prank receiver", NotificationManager.IMPORTANCE_LOW)
            )
            nm?.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL, "Incoming pranks", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SVC_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Prank receiver is ON")
            .setContentText("Buddies on this Wi-Fi can reach your phone")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        PrankBus.setRunning(false)
        PrankBus.clear()
        try { ringtone?.stop() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { mcLock?.release() } catch (_: Exception) {}
        scope.coroutineContext[Job]?.cancel()
    }

    companion object {
        const val SVC_CHANNEL = "prank_svc"
        const val ALERT_CHANNEL = "prank_alert"
        const val NOTIF_ID = 42
        const val ALERT_ID = 43

        fun start(ctx: Context) {
            val i = Intent(ctx, PrankService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, PrankService::class.java))
        }
    }
}
