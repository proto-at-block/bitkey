package build.wallet.fwup

import bitkey.account.HardwareType
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.A
import build.wallet.fwup.FwupDataFetcherImpl.Companion.FWUP_BUNDLE_DIRECTORY
import build.wallet.platform.data.FileManager
import build.wallet.platform.data.FileManagerResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * Fake implementation of [FirmwareDownloader] for testing with fake hardware.
 *
 * Instead of downloading from the network, this generates mock manifest files
 * with an incremented version number and writes them to the provided [FileManager].
 * This allows the real [FwupManifestParser] and [FwupDataFetcherImpl] to be exercised
 * in fake hardware flows.
 *
 * Manifest version generation:
 * - W1 hardware: V1 manifest (single MCU)
 * - W3 hardware: V2 manifest (multi-MCU with CORE and UXC)
 *
 * Always offers an update one version higher than the device's current version.
 */
@Fake
@BitkeyInject(AppScope::class)
class FirmwareDownloaderFake(
  @Fake private val fileManager: FileManager,
) : FirmwareDownloader {
  override suspend fun download(
    deviceInfo: FirmwareDeviceInfo,
  ): Result<Unit, FirmwareDownloadError> {
    val currentVersion = deviceInfo.version
    val incrementedVersion = incrementSemver(currentVersion)

    // Determine which slot to target based on active slot
    val targetSlot = when (deviceInfo.activeSlot) {
      A -> "b"
      else -> "a"
    }

    // Determine hardware type and generate appropriate manifest
    val isW3 = deviceInfo.hardwareType() == HardwareType.W3

    if (isW3) {
      writeW3ManifestAndFiles(incrementedVersion, targetSlot)
    } else {
      writeW1ManifestAndFiles(incrementedVersion, targetSlot)
    }

    return Ok(Unit)
  }

  /**
   * Writes V1 manifest and firmware files for W1 hardware (single MCU).
   */
  private suspend fun writeW1ManifestAndFiles(
    version: String,
    targetSlot: String,
  ) {
    // Generate and write V1 manifest
    val manifestJson = generateV1ManifestJson(version)
    fileManager.writeFile(
      manifestJson.encodeToByteArray(),
      "$FWUP_BUNDLE_DIRECTORY/fwup-manifest.json"
    ).unwrap()

    // Write mock firmware binary for target slot
    val firmwareData = "mock-w1-firmware-v$version".encodeToByteArray()
    fileManager.writeFile(
      firmwareData,
      "$FWUP_BUNDLE_DIRECTORY/app-$targetSlot.signed.bin"
    ).unwrap()

    // Write mock signature for target slot
    val signatureData = "mock-w1-signature-v$version".encodeToByteArray()
    fileManager.writeFile(
      signatureData,
      "$FWUP_BUNDLE_DIRECTORY/app-$targetSlot.detached_signature"
    ).unwrap()
  }

  /**
   * Writes V2 manifest and firmware files for W3 hardware (multi-MCU: CORE + UXC).
   */
  private suspend fun writeW3ManifestAndFiles(
    version: String,
    targetSlot: String,
  ) {
    // Generate and write V2 manifest
    val manifestJson = generateV2ManifestJson(version)
    fileManager.writeFile(
      manifestJson.encodeToByteArray(),
      "$FWUP_BUNDLE_DIRECTORY/fwup-manifest.json"
    ).unwrap()

    // Write CORE MCU firmware and signature
    val coreFirmwareData = "mock-w3-core-firmware-v$version".encodeToByteArray()
    fileManager.writeFile(
      coreFirmwareData,
      "$FWUP_BUNDLE_DIRECTORY/core-app-$targetSlot.signed.bin"
    ).unwrap()

    val coreSignatureData = "mock-w3-core-signature-v$version".encodeToByteArray()
    fileManager.writeFile(
      coreSignatureData,
      "$FWUP_BUNDLE_DIRECTORY/core-app-$targetSlot.detached_signature"
    ).unwrap()

    // Write UXC MCU firmware and signature
    val uxcFirmwareData = "mock-w3-uxc-firmware-v$version".encodeToByteArray()
    fileManager.writeFile(
      uxcFirmwareData,
      "$FWUP_BUNDLE_DIRECTORY/uxc-app-$targetSlot.signed.bin"
    ).unwrap()

    val uxcSignatureData = "mock-w3-uxc-signature-v$version".encodeToByteArray()
    fileManager.writeFile(
      uxcSignatureData,
      "$FWUP_BUNDLE_DIRECTORY/uxc-app-$targetSlot.detached_signature"
    ).unwrap()
  }

  /**
   * Generates a V1 manifest JSON for W1 hardware (single MCU).
   */
  private fun generateV1ManifestJson(version: String): String {
    return """
      {
        "manifest_version": "0.0.1",
        "fwup_bundle": {
          "product": "w1a",
          "version": "$version",
          "assets": {
            "bootloader": {
              "image": {"name": "loader.signed.bin"},
              "signature": {"name": "loader.detached_signature"}
            },
            "application_a": {
              "image": {"name": "app-a.signed.bin"},
              "signature": {"name": "app-a.detached_signature"}
            },
            "application_b": {
              "image": {"name": "app-b.signed.bin"},
              "signature": {"name": "app-b.detached_signature"}
            }
          },
          "parameters": {
            "wca_chunk_size": 452,
            "signature_offset": 647104,
            "app_properties_offset": 1024
          }
        }
      }
      """.trimIndent()
  }

  /**
   * Generates a V2 manifest JSON for W3 hardware (multi-MCU: CORE + UXC).
   */
  private fun generateV2ManifestJson(version: String): String {
    return """
      {
        "manifest_version": "0.0.2",
        "fwup_bundle": {
          "product": "w3a",
          "version": "$version",
          "mcus": {
            "core": {
              "mcu_name": "efr32",
              "assets": {
                "bootloader": {
                  "image": {"name": "core-loader.signed.bin"},
                  "signature": {"name": "core-loader.detached_signature"}
                },
                "application_a": {
                  "image": {"name": "core-app-a.signed.bin"},
                  "signature": {"name": "core-app-a.detached_signature"}
                },
                "application_b": {
                  "image": {"name": "core-app-b.signed.bin"},
                  "signature": {"name": "core-app-b.detached_signature"}
                }
              },
              "parameters": {
                "wca_chunk_size": 452,
                "signature_offset": 647104,
                "app_properties_offset": 1024
              }
            },
            "uxc": {
              "mcu_name": "stm32u5",
              "assets": {
                "application_a": {
                  "image": {"name": "uxc-app-a.signed.bin"},
                  "signature": {"name": "uxc-app-a.detached_signature"}
                },
                "application_b": {
                  "image": {"name": "uxc-app-b.signed.bin"},
                  "signature": {"name": "uxc-app-b.detached_signature"}
                }
              },
              "parameters": {
                "wca_chunk_size": 448,
                "signature_offset": 524288,
                "app_properties_offset": 1024
              }
            }
          }
        }
      }
      """.trimIndent()
  }

  /**
   * Increments the patch version of a semver string.
   * Falls back to "1.0.1" if parsing fails.
   */
  private fun incrementSemver(version: String): String {
    return try {
      val parts = version.split('.')
      if (parts.size == 3) {
        val major = parts[0].toInt()
        val minor = parts[1].toInt()
        val patch = parts[2].toInt()
        "$major.$minor.${patch + 1}"
      } else {
        "1.0.1"
      }
    } catch (e: NumberFormatException) {
      "1.0.1"
    }
  }

  private fun <T : Any> FileManagerResult<T>.unwrap(): T {
    return when (this) {
      is FileManagerResult.Ok -> value
      is FileManagerResult.Err -> throw error.cause
    }
  }
}
