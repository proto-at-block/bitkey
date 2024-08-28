@file:Suppress("ConstructorParameterNaming", "PropertyName")

package build.wallet.fwup

import build.wallet.catchingResult
import build.wallet.fwup.FwupManifestParser.FwupSlot
import build.wallet.fwup.FwupManifestParser.FwupSlot.A
import build.wallet.fwup.FwupManifestParser.FwupSlot.B
import build.wallet.fwup.FwupManifestParser.ParseFwupManifestSuccess
import build.wallet.fwup.FwupMode.Delta
import build.wallet.fwup.FwupMode.Normal
import build.wallet.fwup.ParseFwupManifestError.NoUpdateNeeded
import build.wallet.fwup.ParseFwupManifestError.ParsingError
import build.wallet.fwup.ParseFwupManifestError.UnknownManifestVersion
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class FwupManifestParserImpl : FwupManifestParser {
  companion object {
    private const val CURRENT_FWUP_MANIFEST_VERSION = "0.0.1"
  }

  override fun parseFwupManifest(
    manifestJson: String,
    currentVersion: String,
    activeSlot: FwupSlot,
    fwupMode: FwupMode,
  ): Result<ParseFwupManifestSuccess, ParseFwupManifestError> =
    when (fwupMode) {
      Normal -> parseNormalFwupManifest(manifestJson, currentVersion, activeSlot)
      Delta -> parseDeltaFwupManifest(manifestJson, currentVersion, activeSlot)
    }

  @Suppress("UnusedParameter", "RedundantSuppression")
  private fun parseNormalFwupManifest(
    manifestJson: String,
    currentVersion: String,
    activeSlot: FwupSlot,
  ): Result<ParseFwupManifestSuccess, ParseFwupManifestError> {
    val manifest =
      catchingResult { Json.decodeFromString<FwupManifest>(manifestJson) }
        .mapError { ParsingError(it) }
        .getOrElse { return Err(it) }

    if (manifest.manifest_version != CURRENT_FWUP_MANIFEST_VERSION) {
      return Err(UnknownManifestVersion)
    }

    if (semverToInt(manifest.fwup_bundle.version) <= semverToInt(currentVersion)) {
      return Err(NoUpdateNeeded)
    }

    // The target slot is the opposite of the currently running slot.
    val targetSlot =
      when (activeSlot) {
        A -> B
        B -> A
      }

    val bundle = manifest.fwup_bundle

    return Ok(
      ParseFwupManifestSuccess(
        firmwareVersion = manifest.fwup_bundle.version,
        binaryFilename =
          when (targetSlot) {
            A -> bundle.assets.application_a.image.name
            B -> bundle.assets.application_b.image.name
          },
        signatureFilename =
          when (targetSlot) {
            A -> bundle.assets.application_a.signature.name
            B -> bundle.assets.application_b.signature.name
          },
        chunkSize = bundle.parameters.wca_chunk_size,
        signatureOffset = bundle.parameters.signature_offset,
        appPropertiesOffset = bundle.parameters.app_properties_offset
      )
    )
  }

  @Suppress("UnusedParameter", "RedundantSuppression")
  private fun parseDeltaFwupManifest(
    manifestJson: String,
    currentVersion: String,
    activeSlot: FwupSlot,
  ): Result<ParseFwupManifestSuccess, ParseFwupManifestError> {
    val manifest =
      catchingResult { Json.decodeFromString<FwupDeltaManifest>(manifestJson) }
        .mapError { ParsingError(it) }
        .getOrElse { return Err(it) }

    if (manifest.manifest_version != CURRENT_FWUP_MANIFEST_VERSION) {
      return Err(UnknownManifestVersion)
    }

    if (semverToInt(manifest.fwup_bundle.to_version) <= semverToInt(currentVersion)) {
      return Err(NoUpdateNeeded)
    }

    // The target slot is the opposite of the currently running slot.
    val targetSlot =
      when (activeSlot) {
        A -> B
        B -> A
      }

    val bundle = manifest.fwup_bundle

    return Ok(
      ParseFwupManifestSuccess(
        firmwareVersion = manifest.fwup_bundle.to_version,
        binaryFilename =
          when (targetSlot) {
            A -> bundle.assets.b2a_patch.image.name
            B -> bundle.assets.a2b_patch.image.name
          },
        signatureFilename =
          when (targetSlot) {
            A -> bundle.assets.b2a_patch.signature.name
            B -> bundle.assets.a2b_patch.signature.name
          },
        chunkSize = bundle.parameters.wca_chunk_size,
        signatureOffset = bundle.parameters.signature_offset,
        appPropertiesOffset = bundle.parameters.app_properties_offset
      )
    )
  }
}

// String.format only works on Java/Android targets, so this function is a bit complex.
fun semverToInt(semver: String): Int {
  val parts = semver.split('.')

  // Assuming the input is always valid and well-formed
  val major = parts[0].toInt()
  val minor = parts[1].toInt()
  val patch = parts[2].toInt()

  // Format the components with leading zeros
  // 2 digits for major, 2 for minor, and 3 for patch.
  val majorFormatted = major.toString().padStart(2, '0')
  val minorFormatted = minor.toString().padStart(2, '0')
  val patchFormatted = patch.toString().padStart(3, '0')

  // Concatenate the components and convert to Int
  val formattedVersion = majorFormatted + minorFormatted + patchFormatted
  return formattedVersion.toInt()
}

@Serializable
private data class NamedAsset(
  val name: String,
)

@Serializable
private data class AssetInfo(
  val image: NamedAsset,
  val signature: NamedAsset,
)

@Serializable
private data class FwupParameters(
  val wca_chunk_size: UInt,
  val signature_offset: UInt,
  val app_properties_offset: UInt,
)

// Normal manifests

@Serializable
private data class Assets(
  val bootloader: AssetInfo,
  val application_a: AssetInfo,
  val application_b: AssetInfo,
)

@Serializable
private data class FwupBundle(
  val product: String,
  val version: String,
  val assets: Assets,
  val parameters: FwupParameters,
)

@Serializable
private data class FwupManifest(
  val manifest_version: String,
  val fwup_bundle: FwupBundle,
)

// Delta manifests

@Serializable
private data class DeltaAssets(
  val a2b_patch: AssetInfo,
  val b2a_patch: AssetInfo,
)

@Serializable
private data class FwupDeltaBundle(
  val product: String,
  val from_version: String,
  val to_version: String,
  val assets: DeltaAssets,
  val parameters: FwupParameters,
)

@Serializable
private data class FwupDeltaManifest(
  val manifest_version: String,
  val fwup_bundle: FwupDeltaBundle,
)
