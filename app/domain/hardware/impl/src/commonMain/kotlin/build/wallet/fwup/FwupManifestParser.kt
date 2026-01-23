package build.wallet.fwup

import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import com.github.michaelbull.result.Result

interface FwupManifestParser {
  /**
   * Parse a FWUP manifest and return the file names and parameters necessary to
   * perform the FWUP.
   */
  fun parseFwupManifest(
    manifestJson: String,
    currentVersion: String,
    activeSlot: FwupSlot,
    fwupMode: FwupMode,
  ): Result<ParseFwupManifestSuccess, ParseFwupManifestError>

  sealed interface ParseFwupManifestSuccess {
    val firmwareVersion: String

    /**
     * V1 manifest result - single MCU firmware update.
     */
    data class SingleMcu(
      override val firmwareVersion: String,
      val binaryFilename: String,
      val signatureFilename: String,
      val chunkSize: UInt,
      val signatureOffset: UInt,
      val appPropertiesOffset: UInt,
    ) : ParseFwupManifestSuccess

    /**
     * V2 manifest result - multi-MCU firmware update.
     */
    data class MultiMcu(
      override val firmwareVersion: String,
      val mcuUpdates: Map<McuRole, McuUpdate>,
    ) : ParseFwupManifestSuccess

    /**
     * Firmware update information for a single MCU.
     */
    data class McuUpdate(
      val mcuName: McuName,
      val binaryFilename: String,
      val signatureFilename: String,
      val chunkSize: UInt,
      val signatureOffset: UInt,
      val appPropertiesOffset: UInt,
    )
  }

  enum class FwupSlot {
    A,
    B,
  }
}
