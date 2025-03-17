package build.wallet.firmware

import build.wallet.firmware.FirmwareMetadata.FirmwareSlot
import build.wallet.firmware.HwKeyConfig.DEV
import build.wallet.firmware.HwKeyConfig.PROD
import build.wallet.firmware.HwKeyConfig.UNKNOWN

enum class HwKeyConfig {
  DEV,
  PROD,
  UNKNOWN,
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
) {
  // Transform a hwRevision like 'w1a-dvt' to 'dvt'.
  // Memfault prefers the latter.
  private fun hwRevisionWithoutProduct() = hwRevision.split("-").last()

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

  fun fwupHwVersion() =
    when (hwConfig()) {
      PROD -> hwRevisionWithoutProduct() + "-prod"
      DEV, UNKNOWN -> hwRevisionWithoutProduct()
    }

  fun batteryChargeForUninitializedModelGauge(): Int {
    // The reported battery percent from firmware 1.0.65 and below is wrong.
    //
    // This roughly corrects the reported battery charge to represent something closer to the actual
    // charge. Most importantly, this helps make it more clear in the UI that the battery is
    // actually full.
    return (batteryCharge * (100.0 / 85)).coerceAtMost(100.0).toInt()
  }
}
