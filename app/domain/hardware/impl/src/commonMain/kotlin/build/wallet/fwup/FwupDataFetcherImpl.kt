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
import build.wallet.fwup.FwupMode.Delta
import build.wallet.fwup.FwupMode.Normal
import build.wallet.fwup.ParseFwupManifestError.UnknownManifestVersion
import build.wallet.logging.logDebug
import build.wallet.platform.data.FileManager
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import okio.ByteString.Companion.toByteString

@BitkeyInject(AppScope::class)
class FwupDataFetcherImpl(
  private val fileManager: FileManager,
  private val fwupManifestParser: FwupManifestParser,
  private val firmwareDownloader: FirmwareDownloader,
) : FwupDataFetcher {
  companion object {
    const val FWUP_BUNDLE_DIRECTORY = "fwup-bundle"
    const val regularManifestName = "$FWUP_BUNDLE_DIRECTORY/fwup-manifest.json"
    const val deltaManifestName = "$FWUP_BUNDLE_DIRECTORY/fwup-delta-manifest.json"
  }

  override suspend fun fetchLatestFwupData(
    deviceInfo: FirmwareDeviceInfo,
  ): Result<List<McuFwupData>, FwupDataFetcher.FwupDataFetcherError> {
    return coroutineBinding {
      // Get the latest firmware, if there is a newer version than specified by the
      // current manifest
      firmwareDownloader.download(deviceInfo)
        .mapError { DownloadError(it) }
        .bind()

      // Get firmware version and slot.
      val fwupMode =
        getUpdateType()
          .mapError { ParseError(it) }
          .bind()

      val manifestName =
        when (fwupMode) {
          Normal -> regularManifestName
          Delta -> deltaManifestName
        }

      val manifestJson =
        fileManager
          .readFileAsString(manifestName)
          .result
          .mapError { FileError(it) }
          .bind()

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
            fwupMode = fwupMode
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
              fwupMode = fwupMode
            )
          )
        }

        is ParseFwupManifestSuccess.MultiMcu -> {
          manifest.mcuUpdates.map { (mcuRole, mcuUpdate) ->
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
              fwupMode = fwupMode
            )
          }
        }
      }
    }
  }

  private suspend fun getUpdateType(): Result<FwupMode, ParseFwupManifestError> {
    return if (fileManager.fileExists(regularManifestName)) {
      logDebug { "Regular manifest exists " }
      Ok(Normal)
    } else if (fileManager.fileExists(deltaManifestName)) {
      logDebug { "Delta manifest exists " }
      Ok(Delta)
    } else {
      Err(UnknownManifestVersion)
    }
  }
}
