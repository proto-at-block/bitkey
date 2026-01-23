package build.wallet.fwup

import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import okio.ByteString
import kotlin.math.ceil

/**
 * Calculates the final sequence ID for a firmware transfer based on firmware size and chunk size.
 * Used by both single-MCU (W1) and multi-MCU (W3) firmware updates.
 */
fun calculateFinalSequenceId(
  firmwareSize: Int,
  chunkSize: UInt,
): UInt {
  if (chunkSize == 0U) return 0U
  return ceil((firmwareSize.toUInt() / chunkSize).toDouble()).toUInt()
}

/**
 * Firmware update data for a single MCU (W1 or W3 CORE/UXC).
 */
data class FwupData(
  /** The version string for the FW, e.g. 1.7.1 */
  val version: String,
  val chunkSize: UInt,
  val signatureOffset: UInt,
  val appPropertiesOffset: UInt,
  val firmware: ByteString,
  val signature: ByteString,
  val fwupMode: FwupMode,
) {
  fun finalSequenceId(): UInt = calculateFinalSequenceId(firmware.size, chunkSize)
}

/**
 * Firmware update data for a specific MCU in a multi-MCU device (W3).
 *
 * @property mcuRole The role of the MCU (CORE or UXC)
 * @property mcuName The name/type of the MCU (EFR32 or STM32U5)
 * @property version The firmware version string, e.g. "2.0.0"
 * @property chunkSize Transfer chunk size for this MCU (may differ per MCU)
 * @property signatureOffset Offset of signature in firmware binary
 * @property appPropertiesOffset Offset of app properties in firmware binary
 * @property firmware The firmware binary data
 * @property signature The signature for verification
 * @property fwupMode Update mode (Normal or Delta)
 */
data class McuFwupData(
  val mcuRole: McuRole,
  val mcuName: McuName,
  val version: String,
  val chunkSize: UInt,
  val signatureOffset: UInt,
  val appPropertiesOffset: UInt,
  val firmware: ByteString,
  val signature: ByteString,
  val fwupMode: FwupMode,
) {
  fun finalSequenceId(): UInt = calculateFinalSequenceId(firmware.size, chunkSize)
}

/**
 * Complete firmware update bundle for multi-MCU devices (W3).
 * Contains firmware updates for all MCUs that need updating.
 *
 * @property bundleVersion The overall bundle version
 * @property mcuUpdates List of per-MCU updates, ordered with CORE first
 */
data class FwupBundleData(
  val bundleVersion: String,
  val mcuUpdates: List<McuFwupData>,
) {
  init {
    require(mcuUpdates.isNotEmpty()) { "mcuUpdates cannot be empty" }
    // Verify CORE comes first if present
    val coreIndex = mcuUpdates.indexOfFirst { it.mcuRole == McuRole.CORE }
    if (coreIndex > 0) {
      error("CORE MCU must be first in mcuUpdates list")
    }
  }
}
