@file:Suppress("ConstructorParameterNaming", "PropertyName")

package build.wallet.fwup

import bitkey.serialization.json.decodeFromStringResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
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

@BitkeyInject(AppScope::class)
class FwupManifestParserImpl : FwupManifestParser {
  private val json = Json {
    ignoreUnknownKeys = true
  }

  companion object {
    private const val FWUP_MANIFEST_VERSION_V1 = "0.0.1"
    private const val FWUP_MANIFEST_VERSION_V2 = "0.0.2"
  }

  override fun parseFwupManifest(
    manifestJson: String,
    currentVersion: String,
    activeSlot: FwupSlot,
    fwupMode: FwupMode,
  ): Result<ParseFwupManifestSuccess, ParseFwupManifestError> {
    val header =
      json.decodeFromStringResult<FwupManifestHeader>(manifestJson)
        .mapError { ParsingError(it) }
        .getOrElse { return Err(it) }

    return when (header.manifest_version) {
      FWUP_MANIFEST_VERSION_V1 -> when (fwupMode) {
        Normal -> parseNormalFwupManifestV1(manifestJson, currentVersion, activeSlot)
        Delta -> parseDeltaFwupManifestV1(manifestJson, currentVersion, activeSlot)
      }
      FWUP_MANIFEST_VERSION_V2 -> when (fwupMode) {
        Normal -> parseNormalFwupManifestV2(manifestJson, currentVersion, activeSlot)
        Delta -> parseDeltaFwupManifestV2(manifestJson, currentVersion, activeSlot)
      }
      else -> Err(UnknownManifestVersion)
    }
  }

  /**
   * Returns the target slot (opposite of active slot) for firmware updates.
   */
  private fun targetSlot(activeSlot: FwupSlot): FwupSlot =
    when (activeSlot) {
      A -> B
      B -> A
    }

  /**
   * Creates an McuUpdate from an AssetInfo and FwupParameters.
   */
  private fun createMcuUpdate(
    mcuName: McuName,
    asset: AssetInfo,
    parameters: FwupParameters,
  ) = ParseFwupManifestSuccess.McuUpdate(
    mcuName = mcuName,
    binaryFilename = asset.image.name,
    signatureFilename = asset.signature.name,
    chunkSize = parameters.wca_chunk_size,
    signatureOffset = parameters.signature_offset,
    appPropertiesOffset = parameters.app_properties_offset
  )

  /**
   * Creates a SingleMcu result from an AssetInfo and FwupParameters.
   */
  private fun createSingleMcuResult(
    firmwareVersion: String,
    asset: AssetInfo,
    parameters: FwupParameters,
  ) = ParseFwupManifestSuccess.SingleMcu(
    firmwareVersion = firmwareVersion,
    binaryFilename = asset.image.name,
    signatureFilename = asset.signature.name,
    chunkSize = parameters.wca_chunk_size,
    signatureOffset = parameters.signature_offset,
    appPropertiesOffset = parameters.app_properties_offset
  )

  private fun parseNormalFwupManifestV1(
    manifestJson: String,
    currentVersion: String,
    activeSlot: FwupSlot,
  ): Result<ParseFwupManifestSuccess, ParseFwupManifestError> {
    val manifest =
      json.decodeFromStringResult<FwupManifestV1>(manifestJson)
        .mapError { ParsingError(it) }
        .getOrElse { return Err(it) }

    val bundle = manifest.fwup_bundle
    if (semverToInt(bundle.version) <= semverToInt(currentVersion)) {
      return Err(NoUpdateNeeded)
    }

    val target = targetSlot(activeSlot)
    val selectedAsset = when (target) {
      A -> bundle.assets.application_a
      B -> bundle.assets.application_b
    }

    return Ok(createSingleMcuResult(bundle.version, selectedAsset, bundle.parameters))
  }

  private fun parseDeltaFwupManifestV1(
    manifestJson: String,
    currentVersion: String,
    activeSlot: FwupSlot,
  ): Result<ParseFwupManifestSuccess, ParseFwupManifestError> {
    val manifest =
      json.decodeFromStringResult<FwupDeltaManifestV1>(manifestJson)
        .mapError { ParsingError(it) }
        .getOrElse { return Err(it) }

    val bundle = manifest.fwup_bundle
    if (semverToInt(bundle.to_version) <= semverToInt(currentVersion)) {
      return Err(NoUpdateNeeded)
    }

    val target = targetSlot(activeSlot)
    val selectedAsset = when (target) {
      A -> bundle.assets.b2a_patch
      B -> bundle.assets.a2b_patch
    }

    return Ok(createSingleMcuResult(bundle.to_version, selectedAsset, bundle.parameters))
  }

  private fun parseNormalFwupManifestV2(
    manifestJson: String,
    currentVersion: String,
    activeSlot: FwupSlot,
  ): Result<ParseFwupManifestSuccess, ParseFwupManifestError> {
    val manifest =
      json.decodeFromStringResult<FwupManifestV2>(manifestJson)
        .mapError { ParsingError(it) }
        .getOrElse { return Err(it) }

    val bundle = manifest.fwup_bundle
    if (semverToInt(bundle.version) <= semverToInt(currentVersion)) {
      return Err(NoUpdateNeeded)
    }

    val target = targetSlot(activeSlot)
    val mcuUpdates = bundle.mcus.mapValues { (_, mcu) ->
      val selectedAsset = when (target) {
        A -> mcu.assets.application_a
        B -> mcu.assets.application_b
      }
      createMcuUpdate(mcu.mcu_name, selectedAsset, mcu.parameters)
    }

    return Ok(ParseFwupManifestSuccess.MultiMcu(bundle.version, mcuUpdates))
  }

  private fun parseDeltaFwupManifestV2(
    manifestJson: String,
    currentVersion: String,
    activeSlot: FwupSlot,
  ): Result<ParseFwupManifestSuccess, ParseFwupManifestError> {
    val manifest =
      json.decodeFromStringResult<FwupDeltaManifestV2>(manifestJson)
        .mapError { ParsingError(it) }
        .getOrElse { return Err(it) }

    val bundle = manifest.fwup_bundle
    if (semverToInt(bundle.to_version) <= semverToInt(currentVersion)) {
      return Err(NoUpdateNeeded)
    }

    val target = targetSlot(activeSlot)
    val mcuUpdates = bundle.mcus.mapValues { (_, mcu) ->
      val selectedAsset = when (target) {
        A -> mcu.assets.b2a_patch
        B -> mcu.assets.a2b_patch
      }
      createMcuUpdate(mcu.mcu_name, selectedAsset, mcu.parameters)
    }

    return Ok(ParseFwupManifestSuccess.MultiMcu(bundle.to_version, mcuUpdates))
  }
}

@Serializable
private data class FwupManifestHeader(
  val manifest_version: String,
)

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

@Serializable
private data class FwupManifestV1(
  val manifest_version: String,
  val fwup_bundle: Bundle,
) {
  @Serializable
  data class Bundle(
    val product: String,
    val version: String,
    val assets: Assets,
    val parameters: FwupParameters,
  )

  @Serializable
  data class Assets(
    val bootloader: AssetInfo,
    val application_a: AssetInfo,
    val application_b: AssetInfo,
  )
}

@Serializable
private data class FwupDeltaManifestV1(
  val manifest_version: String,
  val fwup_bundle: Bundle,
) {
  @Serializable
  data class Bundle(
    val product: String,
    val from_version: String,
    val to_version: String,
    val assets: Assets,
    val parameters: FwupParameters,
  )

  @Serializable
  data class Assets(
    val a2b_patch: AssetInfo,
    val b2a_patch: AssetInfo,
  )
}

@Serializable
private data class FwupManifestV2(
  val manifest_version: String,
  val fwup_bundle: Bundle,
) {
  @Serializable
  data class Bundle(
    val product: String,
    val version: String,
    val mcus: Map<McuRole, McuInfo>,
  )

  @Serializable
  data class McuInfo(
    val mcu_name: McuName,
    val assets: Assets,
    val parameters: FwupParameters,
  )

  @Serializable
  data class Assets(
    val bootloader: AssetInfo? = null,
    val application_a: AssetInfo,
    val application_b: AssetInfo,
  )
}

@Serializable
private data class FwupDeltaManifestV2(
  val manifest_version: String,
  val fwup_bundle: Bundle,
) {
  @Serializable
  data class Bundle(
    val product: String,
    val from_version: String,
    val to_version: String,
    val mcus: Map<McuRole, McuInfo>,
  )

  @Serializable
  data class McuInfo(
    val mcu_name: McuName,
    val assets: Assets,
    val parameters: FwupParameters,
  )

  @Serializable
  data class Assets(
    val a2b_patch: AssetInfo,
    val b2a_patch: AssetInfo,
  )
}
