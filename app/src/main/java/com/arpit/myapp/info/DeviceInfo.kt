package com.arpit.myapp.info

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.Display
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class InfoSection(val emoji: String, val title: String, val rows: List<Pair<String, String>>)

object DeviceInfo {

    fun collect(ctx: Context): List<InfoSection> = listOf(
        safe("📱", "Device") { device() },
        safe("🤖", "System") { system(ctx) },
        safe("🔋", "Battery") { battery(ctx) },
        safe("🧠", "Processor") { cpu() },
        safe("💾", "Memory & storage") { memory(ctx) },
        safe("🖥️", "Display") { display(ctx) },
        safe("📷", "Cameras") { cameras(ctx) },
        safe("✅", "Features") { features(ctx) },
        sensors(ctx),
    ).filter { it.rows.isNotEmpty() }

    private fun safe(emoji: String, title: String, block: () -> List<Pair<String, String>>): InfoSection =
        try { InfoSection(emoji, title, block()) } catch (e: Exception) {
            InfoSection(emoji, title, listOf("Error" to (e.message ?: "Unavailable")))
        }

    // ---------- helpers ----------

    fun bytes(b: Long): String {
        val gb = b / 1_073_741_824.0
        return if (gb >= 1) String.format(Locale.US, "%.1f GB", gb)
        else String.format(Locale.US, "%.0f MB", b / 1_048_576.0)
    }

    private fun yes(b: Boolean) = if (b) "Yes" else "No"

    private fun cap(s: String) = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    private fun duration(ms: Long): String {
        val m = ms / 60_000
        val d = m / (60 * 24)
        val h = (m / 60) % 24
        val mm = m % 60
        return buildString {
            if (d > 0) append("${d}d ")
            if (d > 0 || h > 0) append("${h}h ")
            append("${mm}m")
        }
    }

    // ---------- sections ----------

    private fun device() = listOf(
        "Manufacturer" to cap(Build.MANUFACTURER),
        "Brand" to cap(Build.BRAND),
        "Model" to Build.MODEL,
        "Device code" to Build.DEVICE,
        "Product" to Build.PRODUCT,
        "Board" to Build.BOARD,
        "Hardware" to Build.HARDWARE,
        "Bootloader" to Build.BOOTLOADER,
    )

    private fun system(ctx: Context): List<Pair<String, String>> {
        val df = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return listOf(
            "Android version" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Security patch" to Build.VERSION.SECURITY_PATCH,
            "Build number" to Build.DISPLAY,
            "Build date" to df.format(Date(Build.TIME)),
            "Kernel" to (System.getProperty("os.version") ?: "—"),
            "Runtime" to "${System.getProperty("java.vm.name") ?: "ART"} ${System.getProperty("java.vm.version") ?: ""}".trim(),
            "Up time" to duration(SystemClock.elapsedRealtime()),
            "Language" to Locale.getDefault().displayName,
            "Time zone" to TimeZone.getDefault().id,
            "Dark mode" to (if (dark) "On" else "Off"),
        )
    }

    private fun battery(ctx: Context): List<Pair<String, String>> {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return listOf("Battery" to "Unavailable")
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        val plug = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Charger (AC)"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            8 -> "Dock"
            else -> "Not plugged in"
        }
        val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val tech = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        val cycles = i.getIntExtra("android.os.extra.CYCLE_COUNT", -1)

        val bm = ctx.getSystemService(BatteryManager::class.java)
        val chargeUah = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE

        return buildList {
            add("Level" to if (pct >= 0) "$pct%" else "—")
            add("Status" to status)
            add("Power source" to plug)
            add("Health" to health)
            if (temp != Int.MIN_VALUE) add("Temperature" to String.format(Locale.US, "%.1f °C", temp / 10.0))
            if (volt > 0) add("Voltage" to String.format(Locale.US, "%.2f V", if (volt > 100) volt / 1000.0 else volt.toDouble()))
            if (!tech.isNullOrBlank()) add("Technology" to tech)
            if (chargeUah > 0) add("Charge left" to "${chargeUah / 1000} mAh")
            if (cycles > 0) add("Charge cycles" to cycles.toString())
        }
    }

    private fun cpu(): List<Pair<String, String>> {
        val cores = Runtime.getRuntime().availableProcessors()
        val freqs = (0 until cores).mapNotNull {
            try {
                File("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq")
                    .takeIf { f -> f.canRead() }?.readText()?.trim()?.toLongOrNull()
            } catch (_: Exception) { null }
        }
        return buildList {
            if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != Build.UNKNOWN) {
                add("Chip (SoC)" to "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
            }
            add("Hardware" to Build.HARDWARE)
            add("Cores" to cores.toString())
            freqs.maxOrNull()?.let { add("Max speed" to String.format(Locale.US, "%.2f GHz", it / 1_000_000.0)) }
            val distinct = freqs.distinct().sortedDescending()
            if (distinct.size > 1) {
                add("Core clusters" to distinct.joinToString(" · ") { f ->
                    val n = freqs.count { it == f }
                    String.format(Locale.US, "%d× %.2f GHz", n, f / 1_000_000.0)
                })
            }
            add("Architecture" to Build.SUPPORTED_ABIS.joinToString(", "))
            add("64-bit" to yes(Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()))
        }
    }

    private fun memory(ctx: Context): List<Pair<String, String>> {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val st = StatFs(Environment.getDataDirectory().path)
        val usedRam = mi.totalMem - mi.availMem
        val usedSt = st.totalBytes - st.availableBytes
        return listOf(
            "RAM total" to bytes(mi.totalMem),
            "RAM in use" to "${bytes(usedRam)} (${if (mi.totalMem > 0) usedRam * 100 / mi.totalMem else 0}%)",
            "RAM free" to bytes(mi.availMem),
            "Low on memory" to yes(mi.lowMemory),
            "Storage total" to bytes(st.totalBytes),
            "Storage used" to "${bytes(usedSt)} (${if (st.totalBytes > 0) usedSt * 100 / st.totalBytes else 0}%)",
            "Storage free" to bytes(st.availableBytes),
        )
    }

    private fun display(ctx: Context): List<Pair<String, String>> {
        val dm = ctx.getSystemService(DisplayManager::class.java)
        val d = dm?.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList()
        val mode = d.mode
        val w = mode.physicalWidth
        val h = mode.physicalHeight
        val metrics = ctx.resources.displayMetrics
        val inches = sqrt(
            (w / metrics.xdpi.toDouble()).let { it * it } + (h / metrics.ydpi.toDouble()).let { it * it }
        )
        val rates = d.supportedModes.map { it.refreshRate.roundToInt() }.distinct().sorted()
        return listOf(
            "Resolution" to "${minOf(w, h)} × ${maxOf(w, h)} px",
            "Size (approx.)" to String.format(Locale.US, "%.1f inch", inches),
            "Density" to "${metrics.densityDpi} dpi",
            "Refresh rate now" to "${d.refreshRate.roundToInt()} Hz",
            "Supported rates" to rates.joinToString(", ") { "$it Hz" },
            "Font scale" to "${ctx.resources.configuration.fontScale}×",
        )
    }

    private fun cameras(ctx: Context): List<Pair<String, String>> {
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return emptyList()
        return cm.cameraIdList.mapIndexed { idx, id ->
            val c = cm.getCameraCharacteristics(id)
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                else -> "External"
            }
            val px = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val mp = px?.let { (it.width.toLong() * it.height / 1_000_000.0) }
            val flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val parts = buildList {
                add(facing)
                mp?.let { add(String.format(Locale.US, "%.0f MP", it)) }
                if (flash) add("flash")
            }
            "Camera ${idx + 1}" to parts.joinToString(" · ")
        }
    }

    private fun features(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        fun has(f: String) = yes(pm.hasSystemFeature(f))
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
        return buildList {
            add("5 GHz Wi-Fi" to yes(wm?.is5GHzBandSupported == true))
            if (Build.VERSION.SDK_INT >= 30) add("6 GHz Wi-Fi" to yes(wm?.is6GHzBandSupported == true))
            add("Wi-Fi Direct" to has(PackageManager.FEATURE_WIFI_DIRECT))
            add("Bluetooth LE" to has(PackageManager.FEATURE_BLUETOOTH_LE))
            add("NFC" to has(PackageManager.FEATURE_NFC))
            add("Fingerprint" to has(PackageManager.FEATURE_FINGERPRINT))
            add("Face unlock" to has("android.hardware.biometrics.face"))
            add("GPS" to has(PackageManager.FEATURE_LOCATION_GPS))
            add("Mobile network" to has(PackageManager.FEATURE_TELEPHONY))
            add("IR blaster" to has(PackageManager.FEATURE_CONSUMER_IR))
            add("USB host (OTG)" to has(PackageManager.FEATURE_USB_HOST))
        }
    }

    private fun sensors(ctx: Context): InfoSection {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val list = try { sm?.getSensorList(Sensor.TYPE_ALL).orEmpty() } catch (_: Exception) { emptyList() }
        return InfoSection(
            "🧭", "Sensors (${list.size})",
            list.map { it.name to it.vendor }
        )
    }
}
