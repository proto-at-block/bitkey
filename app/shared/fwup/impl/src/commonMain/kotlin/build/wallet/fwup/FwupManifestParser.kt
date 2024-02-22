package build.wallet.fwup

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

  data class ParseFwupManifestSuccess(
    val firmwareVersion: String,
    val binaryFilename: String,
    val signatureFilename: String,
    val chunkSize: UInt,
    val signatureOffset: UInt,
    val appPropertiesOffset: UInt,
  )

  enum class FwupSlot {
    A,
    B,
  }
}
