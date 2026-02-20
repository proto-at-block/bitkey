package build.wallet.fwup

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.A
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.B
import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import build.wallet.fwup.FwupDataFetcher.FwupDataFetcherError.DownloadError
import build.wallet.fwup.FwupDataFetcher.FwupDataFetcherError.FileError
import build.wallet.fwup.FwupDataFetcher.FwupDataFetcherError.ParseError
import build.wallet.fwup.FwupManifestParser.FwupSlot
import build.wallet.fwup.FwupManifestParser.ParseFwupManifestSuccess
import build.wallet.fwup.ParseFwupManifestError.UnknownManifestVersion
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import okio.ByteString.Companion.toByteString

/**
 * Implementation of [FwupDataFetcher] that fetches firmware update data.
 *
 * This class handles both V1 (single MCU) and V2 (multi-MCU) manifest formats:
 * - V1: Used by W1 hardware with a single MCU
 * - V2: Used by W3 hardware with CORE and UXC MCUs
 *
 * Dependencies are obtained via providers which automatically switch between
 * real and fake implementations based on the account's hardware configuration.
 */
@BitkeyInject(AppScope::class)
class FwupDataFetcherImpl(
  private val fileManagerProvider: FileManagerProvider,
  private val fwupManifestParser: FwupManifestParser,
  private val firmwareDownloaderProvider: FirmwareDownloaderProvider,
) : FwupDataFetcher {
  companion object {
    const val FWUP_BUNDLE_DIRECTORY = "fwup-bundle"
    const val regularManifestName = "$FWUP_BUNDLE_DIRECTORY/fwup-manifest.json"
    const val deltaManifestName = "$FWUP_BUNDLE_DIRECTORY/fwup-delta-manifest.json"
  }

  override suspend fun fetchLatestFwupData(
    deviceInfo: FirmwareDeviceInfo,
  ): Result<List<McuFwupData>, FwupDataFetcher.FwupDataFetcherError> {
    // Get the appropriate implementations based on account config (real vs fake hardware)
    val fileManager = fileManagerProvider.get().value
    val firmwareDownloader = firmwareDownloaderProvider.get().value

    return coroutineBinding {
      // Get the latest firmware, if there is a newer version than specified by the
      // current manifest
      firmwareDownloader.download(deviceInfo)
        .mapError { DownloadError(it) }
        .bind()

      // Read whichever manifest file exists (prefer delta-specific filename, fall back to regular)
      val manifestJson =
        readManifestJson(fileManager)
          .mapError { ParseError(it) }
          .bind()

      // Build map of current MCU versions for per-MCU version checking.
      // When mcuInfo is empty (older single-MCU devices), currentMcuVersions is null,
      // causing the parser to fall back to currentVersion (deviceInfo.version) for all MCUs.
      // This is consistent with FirmwareDownloaderImpl, which queries using deviceInfo.version
      // when mcuInfo is empty.
      val currentMcuVersions = deviceInfo.mcuInfo
        .takeIf { it.isNotEmpty() }
        ?.associate { it.mcuRole to it.firmwareVersion }

      // Parser detects normal vs delta from the manifest content
      val manifest =
        fwupManifestParser
          .parseFwupManifest(
            manifestJson = manifestJson,
            currentVersion = deviceInfo.version,
            activeSlot =
              when (deviceInfo.activeSlot) { // Enum translation
                A -> FwupSlot.A
                B -> FwupSlot.B
              },
            currentMcuVersions = currentMcuVersions
          )
          .mapError { ParseError(it) }
          .bind()

      when (manifest) {
        is ParseFwupManifestSuccess.SingleMcu -> {
          val firmware =
            fileManager
              .readFileAsBytes("$FWUP_BUNDLE_DIRECTORY/${manifest.binaryFilename}")
              .result
              .mapError { FileError(it) }
              .bind()

          val signature =
            fileManager
              .readFileAsBytes("$FWUP_BUNDLE_DIRECTORY/${manifest.signatureFilename}")
              .result
              .mapError { FileError(it) }
              .bind()

          listOf(
            McuFwupData(
              mcuRole = McuRole.CORE,
              mcuName = McuName.EFR32,
              version = manifest.firmwareVersion,
              chunkSize = manifest.chunkSize,
              signatureOffset = manifest.signatureOffset,
              appPropertiesOffset = manifest.appPropertiesOffset,
              firmware = firmware.toByteString(),
              signature = signature.toByteString(),
              fwupMode = manifest.fwupMode
            )
          )
        }

        is ParseFwupManifestSuccess.MultiMcu -> {
          // Sort MCU updates: UXC first, then CORE
          // For W3 hardware, the UXC MCU needs to be updated before the CORE MCU
          val sortedMcuUpdates = manifest.mcuUpdates.entries.sortedBy { (mcuRole, _) ->
            when (mcuRole) {
              McuRole.UXC -> 0 // UXC first
              McuRole.CORE -> 1 // CORE second
            }
          }

          sortedMcuUpdates.map { (mcuRole, mcuUpdate) ->
            val firmware =
              fileManager
                .readFileAsBytes("$FWUP_BUNDLE_DIRECTORY/${mcuUpdate.binaryFilename}")
                .result
                .mapError { FileError(it) }
                .bind()

            val signature =
              fileManager
                .readFileAsBytes("$FWUP_BUNDLE_DIRECTORY/${mcuUpdate.signatureFilename}")
                .result
                .mapError { FileError(it) }
                .bind()

            McuFwupData(
              mcuRole = mcuRole,
              mcuName = mcuUpdate.mcuName,
              version = manifest.firmwareVersion,
              chunkSize = mcuUpdate.chunkSize,
              signatureOffset = mcuUpdate.signatureOffset,
              appPropertiesOffset = mcuUpdate.appPropertiesOffset,
              firmware = firmware.toByteString(),
              signature = signature.toByteString(),
              fwupMode = manifest.fwupMode
            )
          }
        }
      }
    }
  }

  /**
   * Reads the manifest JSON from whichever file exists on disk.
   * Prefers the dedicated delta manifest file, falls back to the regular manifest file.
   * The parser will detect the actual manifest type from the content.
   */
  private suspend fun readManifestJson(
    fileManager: build.wallet.platform.data.FileManager,
  ): Result<String, ParseFwupManifestError> {
    val manifestName = when {
      fileManager.fileExists(deltaManifestName) -> deltaManifestName
      fileManager.fileExists(regularManifestName) -> regularManifestName
      else -> return Err(UnknownManifestVersion)
    }

    return fileManager.readFileAsString(manifestName).result
      .mapError { ParseFwupManifestError.ParsingError(it) }
  }
}
