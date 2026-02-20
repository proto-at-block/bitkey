package build.wallet.fwup

import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import com.github.michaelbull.result.Result

interface FwupManifestParser {
  /**
   * Parse a FWUP manifest and return the file names and parameters necessary to
   * perform the FWUP. Automatically detects whether the manifest is a normal or
   * delta update from its content.
   *
   * @param manifestJson The manifest JSON string
   * @param currentVersion The current firmware version (used as fallback for v1 manifests, when currentMcuVersions is null, or when a specific MCU is not present in the currentMcuVersions map)
   * @param activeSlot The currently active firmware slot
   * @param currentMcuVersions Optional map of MCU role to current version for per-MCU version checking (v2 bundles)
   */
  fun parseFwupManifest(
    manifestJson: String,
    currentVersion: String,
    activeSlot: FwupSlot,
    currentMcuVersions: Map<McuRole, String>? = null,
  ): Result<ParseFwupManifestSuccess, ParseFwupManifestError>

  sealed interface ParseFwupManifestSuccess {
    val firmwareVersion: String
    val fwupMode: FwupMode

    /**
     * V1 manifest result - single MCU firmware update.
     */
    data class SingleMcu(
      override val firmwareVersion: String,
      override val fwupMode: FwupMode,
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
      override val fwupMode: FwupMode,
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
