package com.batttext.app.data.shizuku

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.math.roundToInt

/**
 * Hardware-level device & battery specifications auto-discovered ONCE and cached in memory
 * so we never waste CPU or battery polling sysfs or Shizuku repeatedly.
 */
data class DeviceHardwareSpecs(
    val deviceModel: String = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL.uppercase()}",
    val deviceCodename: String = Build.DEVICE.uppercase(),
    val socName: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim().uppercase()
    } else {
        Build.HARDWARE.uppercase()
    },
    val androidRelease: String = "ANDROID ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val autoDesignCapacityMah: Int = 4500,
    val capacitySource: String = "DEFAULT",
    val kernelFullCapacityMah: Int? = null,
    val cycleCount: Int? = null,
    val fastChargeType: String? = null,
    val maxAdapterWatts: Float? = null,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false
)

class HardwareSpecReader(private val context: Context) {

    @Volatile
    var cachedSpecs: DeviceHardwareSpecs? = null
        private set

    fun isShizukuRunning(): Boolean {
        return runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }

    fun isShizukuPermissionGranted(): Boolean {
        if (!isShizukuRunning()) return false
        return runCatching {
            if (Shizuku.isPreV11()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun requestShizukuPermission(requestCode: Int = 4001) {
        runCatching {
            if (isShizukuRunning() && !isShizukuPermissionGranted()) {
                Shizuku.requestPermission(requestCode)
            }
        }
    }

    /**
     * Discovers all device and battery hardware specs using:
     * 1. Shizuku privileged shell (`dumpsys battery` & `/sys/class/power_supply/battery/`) when granted
     * 2. Direct world-readable `/sys/class/power_supply/battery/` sysfs nodes
     * 3. Android internal `com.android.internal.os.PowerProfile` reflection (`getBatteryCapacity()`)
     */
    fun discoverSpecs(forceRefresh: Boolean = false): DeviceHardwareSpecs {
        val existing = cachedSpecs
        if (!forceRefresh && existing != null) return existing

        val shizukuAlive = isShizukuRunning()
        val shizukuOk = isShizukuPermissionGranted()

        // Step 1: Read PowerProfile nominal design capacity via reflection (works without any permissions)
        val powerProfileCap = readPowerProfileCapacityMah()

        // Step 2: Try direct non-root sysfs reads first
        var sysfsDesignCap = readSysfsIntNormalizedMah(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design"
        )
        var sysfsLearnedFullCap = readSysfsIntNormalizedMah(
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/bms/charge_full"
        )
        var sysfsCycles = readSysfsRawInt(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/bms/cycle_count"
        )?.takeIf { it in 0..10_000 }
        var chargeType = readSysfsString(
            "/sys/class/power_supply/battery/charge_type",
            "/sys/class/power_supply/usb/real_type"
        )
        var maxWatts: Float? = null

        // Step 3: If Shizuku is granted, run a single batched shell query to read protected kernel nodes + dumpsys battery
        if (shizukuOk) {
            val shizukuData = queryViaShizuku()
            if (shizukuData.designCapMah != null) sysfsDesignCap = shizukuData.designCapMah
            if (shizukuData.learnedFullCapMah != null) sysfsLearnedFullCap = shizukuData.learnedFullCapMah
            if (shizukuData.cycleCount != null) sysfsCycles = shizukuData.cycleCount
            if (!shizukuData.chargeType.isNullOrBlank()) chargeType = shizukuData.chargeType
            if (shizukuData.maxWatts != null) maxWatts = shizukuData.maxWatts
        }

        // Choose the most authoritative Design Capacity found
        val (finalDesignMah, sourceLabel) = when {
            sysfsDesignCap != null && sysfsDesignCap in 1000..25000 ->
                sysfsDesignCap to (if (shizukuOk) "SHIZUKU/SYSFS" else "KERNEL SYSFS")
            powerProfileCap != null && powerProfileCap in 1000..25000 ->
                powerProfileCap to "POWER_PROFILE"
            sysfsLearnedFullCap != null && sysfsLearnedFullCap in 1000..25000 ->
                sysfsLearnedFullCap to "LEARNED_FULL"
            else -> 5000 to "FALLBACK"
        }

        val socRaw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val m = Build.SOC_MANUFACTURER.takeIf { it != Build.UNKNOWN } ?: ""
            val s = Build.SOC_MODEL.takeIf { it != Build.UNKNOWN } ?: Build.BOARD
            "$m $s".trim().uppercase()
        } else {
            "${Build.HARDWARE} (${Build.BOARD})".uppercase()
        }

        val specs = DeviceHardwareSpecs(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().uppercase(),
            deviceCodename = "${Build.DEVICE} (${Build.PRODUCT})".uppercase(),
            socName = socRaw.ifBlank { Build.BOARD.uppercase() },
            androidRelease = "ANDROID ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            autoDesignCapacityMah = finalDesignMah,
            capacitySource = sourceLabel,
            kernelFullCapacityMah = sysfsLearnedFullCap,
            cycleCount = sysfsCycles,
            fastChargeType = chargeType?.uppercase(),
            maxAdapterWatts = maxWatts,
            shizukuRunning = shizukuAlive,
            shizukuGranted = shizukuOk
        )
        cachedSpecs = specs
        return specs
    }

    @SuppressLint("PrivateApi")
    private fun readPowerProfileCapacityMah(): Int? {
        return runCatching {
            val cls = Class.forName("com.android.internal.os.PowerProfile")
            val instance = cls.getConstructor(Context::class.java).newInstance(context)
            val capDouble = cls.getMethod("getBatteryCapacity").invoke(instance) as? Double
            capDouble?.roundToInt()?.takeIf { it in 1000..25000 }
        }.getOrNull()
    }

    private fun readSysfsIntNormalizedMah(vararg paths: String): Int? {
        for (p in paths) {
            val raw = readSysfsRawInt(p) ?: continue
            val normalized = normalizeCapacityMah(raw)
            if (normalized != null) return normalized
        }
        return null
    }

    private fun normalizeCapacityMah(raw: Int): Int? {
        return when {
            raw in 1000..25000 -> raw // already in mAh
            raw in 1_000_000..25_000_000 -> (raw / 1000f).roundToInt() // in µAh
            raw in 100_000..999_999 -> (raw / 100f).roundToInt() // in 10µAh (some Xiaomi/MTK kernels)
            else -> null
        }
    }

    private fun readSysfsRawInt(vararg paths: String): Int? {
        for (p in paths) {
            val f = File(p)
            if (f.exists() && f.canRead()) {
                val v = runCatching { f.readText().trim().toIntOrNull() }.getOrNull()
                if (v != null && v > 0) return v
            }
        }
        return null
    }

    private fun readSysfsString(vararg paths: String): String? {
        for (p in paths) {
            val f = File(p)
            if (f.exists() && f.canRead()) {
                val s = runCatching { f.readText().trim() }.getOrNull()
                if (!s.isNullOrBlank() && !s.equals("unknown", ignoreCase = true)) return s
            }
        }
        return null
    }

    private data class ShizukuBatteryData(
        val designCapMah: Int? = null,
        val learnedFullCapMah: Int? = null,
        val cycleCount: Int? = null,
        val chargeType: String? = null,
        val maxWatts: Float? = null
    )

    /**
     * Uses Shizuku.newProcess (via reflection so it compiles cleanly against public Shizuku API)
     * to run a single fast shell command reading kernel battery sysfs and dumpsys battery.
     */
    private fun queryViaShizuku(): ShizukuBatteryData {
        return runCatching {
            val cmd = arrayOf(
                "sh",
                "-c",
                "cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null || echo -1; " +
                    "cat /sys/class/power_supply/battery/charge_full 2>/dev/null || echo -1; " +
                    "cat /sys/class/power_supply/battery/cycle_count 2>/dev/null || echo -1; " +
                    "cat /sys/class/power_supply/battery/charge_type 2>/dev/null || echo NONE; " +
                    "dumpsys battery"
            )
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val proc = newProcessMethod.invoke(null, cmd, null, null) as Process
            val lines = BufferedReader(InputStreamReader(proc.inputStream)).use { it.readLines() }
            proc.waitFor()

            val rawDesign = lines.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            val rawFull = lines.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            val rawCycles = lines.getOrNull(2)?.trim()?.toIntOrNull()?.takeIf { it in 0..10_000 }
            val rawType = lines.getOrNull(3)?.trim()?.takeIf { it != "NONE" && it.isNotBlank() }

            var dumpsysCapMah: Int? = null
            var dumpsysCycles: Int? = null
            var maxChargingMicroAmp = 0
            var maxChargingMicroVolt = 0

            for (line in lines.drop(4)) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("Charge counter:", ignoreCase = true) -> {
                        // Handled dynamically by BatteryManager, but useful if needed
                    }
                    trimmed.startsWith("Max charging current:", ignoreCase = true) -> {
                        maxChargingMicroAmp = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    trimmed.startsWith("Max charging voltage:", ignoreCase = true) -> {
                        maxChargingMicroVolt = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    trimmed.contains("cycle", ignoreCase = true) && dumpsysCycles == null -> {
                        val num = trimmed.substringAfter(":").trim().toIntOrNull()
                        if (num != null && num in 1..10_000) dumpsysCycles = num
                    }
                    trimmed.contains("capacity", ignoreCase = true) && dumpsysCapMah == null -> {
                        val num = trimmed.substringAfter(":").trim().toIntOrNull()
                        if (num != null) dumpsysCapMah = normalizeCapacityMah(num)
                    }
                }
            }

            val amp = if (maxChargingMicroAmp > 50_000) maxChargingMicroAmp / 1_000_000f else maxChargingMicroAmp / 1000f
            val volt = if (maxChargingMicroVolt > 50_000) maxChargingMicroVolt / 1_000_000f else maxChargingMicroVolt / 1000f
            val adapterWatts = if (amp > 0f && volt > 0f) (amp * volt).takeIf { it in 2f..240f } else null

            ShizukuBatteryData(
                designCapMah = rawDesign?.let { normalizeCapacityMah(it) } ?: dumpsysCapMah,
                learnedFullCapMah = rawFull?.let { normalizeCapacityMah(it) },
                cycleCount = rawCycles ?: dumpsysCycles,
                chargeType = rawType,
                maxWatts = adapterWatts
            )
        }.getOrDefault(ShizukuBatteryData())
    }
}
