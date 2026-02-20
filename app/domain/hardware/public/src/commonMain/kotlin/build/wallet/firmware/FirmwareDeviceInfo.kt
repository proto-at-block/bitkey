package build.wallet.firmware

import bitkey.account.HardwareType
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot
import build.wallet.firmware.HwKeyConfig.DEV
import build.wallet.firmware.HwKeyConfig.PROD
import build.wallet.firmware.HwKeyConfig.UNKNOWN
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class HwKeyConfig {
  DEV,
  PROD,
  UNKNOWN,
}

@Serializable
enum class McuRole {
  @SerialName("core")
  CORE,

  @SerialName("uxc")
  UXC,
}

@Serializable
enum class McuName {
  @SerialName("efr32")
  EFR32,

  @SerialName("stm32u5")
  STM32U5,
}

data class TemplateMatchStats(
  val passCount: Long,
  val firmwareVersion: String,
) {
  override fun toString(): String {
    return "passCount: $passCount, firmwareVersion: $firmwareVersion"
  }
}

data class BioMatchStats(
  val passCounts: List<TemplateMatchStats>,
  val failCount: Long,
) {
  override fun toString(): String {
    return passCounts.mapIndexed { index, templateMatchStats ->
      "passCounts[$index]: $templateMatchStats"
    }.joinToString(", ") + ", failCount: $failCount"
  }
}

data class McuInfo(
  val mcuRole: McuRole,
  val mcuName: McuName,
  val firmwareVersion: String,
) {
  override fun toString(): String {
    val role = mcuRole.name
    val name = mcuName.name
    return "role: $role, name: $name, firmwareVersion: $firmwareVersion"
  }
}

/**
 * @version Firmware version number.
 * @serial Hardware's top-level (assembly) serial number.
 * @swType Firmware build variant.
 * @hwRevision Hardware variant (e.g. dvt, evt).
 * @activeSlot Active firmware slot (A or B).
 * @batteryCharge Battery charge level in millipercent (e.g. 80.45%)
 * @vCell Battery voltage in millivolts.
 * @avgCurrentMa Average current draw in milliamps.
 * @batteryCycles Number of battery cycles.
 * @secureBootConfig Secure boot configuration.
 * @timeRetrieved Time this information was retrieved in Unix epoch seconds.
 * @bioMatchStats Fingerprint match statistics. This field SHOULD NOT be persisted. It should only be used for telemetry.
 * @mcuInfo Information about the MCUs present in the device.
 */
data class FirmwareDeviceInfo(
  val version: String,
  val serial: String,
  val swType: String,
  val hwRevision: String,
  val activeSlot: FirmwareSlot,
  val batteryCharge: Double,
  val vCell: Long,
  val avgCurrentMa: Long, // Signed
  val batteryCycles: Long,
  val secureBootConfig: SecureBootConfig,
  val timeRetrieved: Long,
  val bioMatchStats: BioMatchStats?,
  val mcuInfo: List<McuInfo>,
) {
  /**
   * Detects the hardware type from the hardware revision string.
   * Hardware revision format:
   * - W1: "{product}-{stage}" (e.g., "w1a-dvt")
   * - W3: "{product}-{mcu}-{stage}" (e.g., "w3a-core-evt")
   */
  fun hardwareType(): HardwareType {
    return when {
      hwRevision.startsWith("w3", ignoreCase = true) -> HardwareType.W3
      hwRevision.startsWith("w1", ignoreCase = true) -> HardwareType.W1
      else -> HardwareType.W1 // Default to W1 for unknown/legacy hardware
    }
  }

  // Transform a hwRevision like 'w1a-dvt' to 'dvt'.
  private fun hwRevisionWithoutProduct() = hwRevision.split("-").last()

  /**
   * Transform a W3 hwRevision like 'w3a-core-evt' to 'w3a-evt' (removes MCU part).
   * Format: {product}-{mcu}-{stage} → {product}-{stage}
   */
  private fun w3HwRevisionWithoutMcu(): String {
    val parts = hwRevision.split("-")
    return if (parts.size >= 3) {
      "${parts.first()}-${parts.last()}"
    } else {
      hwRevision // Fallback if format doesn't match
    }
  }

  private fun hwConfig(): HwKeyConfig =
    when (secureBootConfig) {
      SecureBootConfig.DEV -> DEV
      SecureBootConfig.PROD -> PROD
      else -> {
        // Fall back to checking the serial number for older firmware versions.
        // This case should only be hit for external beta customers and internal team members.
        when {
          hwRevision.contains("dvt") ->
            when {
              // Read engineering flag from serial.
              // For DVT: 8 || 9 == prod
              // For MP: 0 == prod
              // But, MP units may have FW with dvt in the name.
              serial[10] == '8' || serial[10] == '9' || serial[10] == '0' -> PROD
              else -> DEV
            }

          swType.endsWith("-dev") -> DEV
          swType.endsWith("-prod") -> PROD
          else -> UNKNOWN
        }
      }
    }

  /**
   * Returns the hardware version string used for querying Memfault for firmware updates.
   * For W1: returns stage only (e.g., "dvt" or "dvt-prod") - preserves existing format
   * For W3: returns <product>-<stage>-<config> (e.g., "w3a-evt-dev" or "w3a-evt-prod")
   *         Input hwRevision is "w3a-core-evt", MCU part is stripped.
   */
  fun fwupHwVersion(): String {
    val base = if (hardwareType() == HardwareType.W3) w3HwRevisionWithoutMcu() else hwRevisionWithoutProduct()
    return when {
      hardwareType() == HardwareType.W3 -> when (hwConfig()) {
        PROD -> "$base-prod"
        DEV, UNKNOWN -> "$base-dev"
      }
      else -> when (hwConfig()) {
        PROD -> "$base-prod"
        DEV, UNKNOWN -> base
      }
    }
  }

  fun batteryChargeForUninitializedModelGauge(): Int {
    // The reported battery percent from firmware 1.0.65 and below is wrong.
    //
    // This roughly corrects the reported battery charge to represent something closer to the actual
    // charge. Most importantly, this helps make it more clear in the UI that the battery is
    // actually full.
    return (batteryCharge * (100.0 / 85)).coerceAtMost(100.0).toInt()
  }

  fun mcuInfo(): String = mcuInfo.joinToString("/") { "${it.mcuRole}:${it.firmwareVersion}" }
}
