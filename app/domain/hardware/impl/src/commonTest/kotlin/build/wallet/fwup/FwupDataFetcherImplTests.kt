package build.wallet.fwup

import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.A
import build.wallet.firmware.McuInfo
import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import build.wallet.firmware.SecureBootConfig
import build.wallet.platform.data.FileManager
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Integration tests for [FwupDataFetcherImpl] — exercises the full pipeline from
 * manifest JSON + firmware files on disk through to an ordered list of [McuFwupData].
 *
 * Each test describes:
 *   - **Device state**: current MCU versions and active slots
 *   - **Bundle from Memfault**: the manifest version and firmware files available
 *   - **Expected result**: the planned update list (which MCUs, in what order)
 *
 * Uses [FileManagerFake] (in-memory) and the real [FwupManifestParserImpl].
 * The downloader is a no-op stub since manifest + firmware files are pre-written.
 */
class FwupDataFetcherImplTests : FunSpec({

  val fileManager = FileManagerFake()

  val fetcher = FwupDataFetcherImpl(
    fileManagerProvider = fixedFileManagerProvider(fileManager),
    fwupManifestParser = FwupManifestParserImpl(),
    firmwareDownloaderProvider = fixedDownloaderProvider(NoOpFirmwareDownloader)
  )

  beforeTest {
    fileManager.reset()
  }

  test("W3 happy path - both MCUs at same version, normal update available") {
    // ── Device state ──
    val deviceInfo = w3DeviceInfo(
      coreVersion = "1.0.0", coreSlot = A,
      uxcVersion = "1.0.0", uxcSlot = A
    )

    // ── Bundle from Memfault ──
    writeW3NormalBundle(fileManager, bundleVersion = "2.0.0")

    // ── Execute ──
    val updates = fetcher.fetchLatestFwupData(deviceInfo).get().shouldNotBeNull()

    // ── Expected: both MCUs need updating, UXC first, targeting slot B ──
    updates.shouldHaveSize(2)

    updates[0].mcuRole.shouldBe(McuRole.UXC)
    updates[0].version.shouldBe("2.0.0")
    updates[0].mcuName.shouldBe(McuName.STM32U5)
    updates[0].fwupMode.shouldBe(FwupMode.Normal)
    // Active slot A → targets slot B
    updates[0].firmware.utf8().shouldBe("fw-uxc-b")
    updates[0].signature.utf8().shouldBe("sig-uxc-b")

    updates[1].mcuRole.shouldBe(McuRole.CORE)
    updates[1].version.shouldBe("2.0.0")
    updates[1].mcuName.shouldBe(McuName.EFR32)
    updates[1].fwupMode.shouldBe(FwupMode.Normal)
    updates[1].firmware.utf8().shouldBe("fw-core-b")
    updates[1].signature.utf8().shouldBe("sig-core-b")
  }

  test("W3 interrupted update - UXC already updated, only CORE needs update") {
    // ── Device state ──
    // UXC was updated to 2.0.0 in a previous session, but the CORE update
    // was never completed (customer killed the app or lost NFC connection).
    val deviceInfo = w3DeviceInfo(
      coreVersion = "1.0.0", coreSlot = A,
      uxcVersion = "2.0.0", uxcSlot = A
    )

    // ── Bundle from Memfault ──
    writeW3NormalBundle(fileManager, bundleVersion = "2.0.0")

    // ── Execute ──
    val updates = fetcher.fetchLatestFwupData(deviceInfo).get().shouldNotBeNull()

    // ── Expected: only CORE needs updating (UXC is already at 2.0.0) ──
    updates.shouldHaveSize(1)

    updates[0].mcuRole.shouldBe(McuRole.CORE)
    updates[0].version.shouldBe("2.0.0")
    updates[0].mcuName.shouldBe(McuName.EFR32)
    updates[0].fwupMode.shouldBe(FwupMode.Normal)
  }

  test("W3 delta happy path - both MCUs at same version, delta update available") {
    // ── Device state ──
    val deviceInfo = w3DeviceInfo(
      coreVersion = "1.0.0", coreSlot = A,
      uxcVersion = "1.0.0", uxcSlot = A
    )

    // ── Bundle from Memfault ──
    writeW3DeltaBundle(fileManager, fromVersion = "1.0.0", toVersion = "2.0.0")

    // ── Execute ──
    val updates = fetcher.fetchLatestFwupData(deviceInfo).get().shouldNotBeNull()

    // ── Expected: both MCUs need updating, UXC first, delta mode ──
    updates.shouldHaveSize(2)

    updates[0].mcuRole.shouldBe(McuRole.UXC)
    updates[0].version.shouldBe("2.0.0")
    updates[0].fwupMode.shouldBe(FwupMode.Delta)

    updates[1].mcuRole.shouldBe(McuRole.CORE)
    updates[1].version.shouldBe("2.0.0")
    updates[1].fwupMode.shouldBe(FwupMode.Delta)
  }

  test("W3 delta interrupted update - UXC already updated, only CORE needs delta") {
    // ── Device state ──
    // UXC was updated to 2.0.0 in a previous session, CORE still at 1.0.0.
    val deviceInfo = w3DeviceInfo(
      coreVersion = "1.0.0", coreSlot = A,
      uxcVersion = "2.0.0", uxcSlot = A
    )

    // ── Bundle from Memfault ──
    writeW3DeltaBundle(fileManager, fromVersion = "1.0.0", toVersion = "2.0.0")

    // ── Execute ──
    val updates = fetcher.fetchLatestFwupData(deviceInfo).get().shouldNotBeNull()

    // ── Expected: only CORE needs delta update (UXC already at 2.0.0) ──
    updates.shouldHaveSize(1)

    updates[0].mcuRole.shouldBe(McuRole.CORE)
    updates[0].version.shouldBe("2.0.0")
    updates[0].fwupMode.shouldBe(FwupMode.Delta)
  }
})

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Builds a W3 [FirmwareDeviceInfo] with per-MCU version and slot info.
 */
private fun w3DeviceInfo(
  coreVersion: String,
  coreSlot: FirmwareSlot,
  uxcVersion: String,
  uxcSlot: FirmwareSlot,
) = FirmwareDeviceInfo(
  version = coreVersion,
  serial = "test-serial",
  swType = "dev",
  hwRevision = "w3a-core-evt",
  activeSlot = coreSlot,
  batteryCharge = 90.0,
  vCell = 4000,
  avgCurrentMa = 10,
  batteryCycles = 5,
  secureBootConfig = SecureBootConfig.DEV,
  timeRetrieved = 0,
  bioMatchStats = null,
  mcuInfo = listOf(
    McuInfo(
      mcuRole = McuRole.CORE,
      mcuName = McuName.EFR32,
      firmwareVersion = coreVersion
    ),
    McuInfo(
      mcuRole = McuRole.UXC,
      mcuName = McuName.STM32U5,
      firmwareVersion = uxcVersion
    )
  )
)

/**
 * Writes a V2 normal (full image) manifest and mock firmware/signature files
 * for both CORE and UXC MCUs. Firmware targets slot B (application_b).
 */
private suspend fun writeW3NormalBundle(
  fileManager: FileManager,
  bundleVersion: String,
) {
  val dir = FwupDataFetcherImpl.FWUP_BUNDLE_DIRECTORY

  // Manifest
  val manifestJson = """
    {
      "manifest_version": "0.0.2",
      "fwup_bundle": {
        "product": "w3a",
        "version": "$bundleVersion",
        "mcus": {
          "core": {
            "mcu_name": "efr32",
            "assets": {
              "application_a": {
                "image": {"name": "core-app-a.bin"},
                "signature": {"name": "core-app-a.sig"}
              },
              "application_b": {
                "image": {"name": "core-app-b.bin"},
                "signature": {"name": "core-app-b.sig"}
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
                "image": {"name": "uxc-app-a.bin"},
                "signature": {"name": "uxc-app-a.sig"}
              },
              "application_b": {
                "image": {"name": "uxc-app-b.bin"},
                "signature": {"name": "uxc-app-b.sig"}
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

  fileManager.writeFile(manifestJson.encodeToByteArray(), "$dir/fwup-manifest.json")

  // Mock firmware and signature files for both slots of both MCUs
  for (mcu in listOf("core", "uxc")) {
    for (slot in listOf("a", "b")) {
      fileManager.writeFile("fw-$mcu-$slot".encodeToByteArray(), "$dir/$mcu-app-$slot.bin")
      fileManager.writeFile("sig-$mcu-$slot".encodeToByteArray(), "$dir/$mcu-app-$slot.sig")
    }
  }
}

/**
 * Writes a V2 delta manifest and mock patch/signature files for both CORE and UXC MCUs.
 */
private suspend fun writeW3DeltaBundle(
  fileManager: FileManager,
  fromVersion: String,
  toVersion: String,
) {
  val dir = FwupDataFetcherImpl.FWUP_BUNDLE_DIRECTORY

  val manifestJson = """
    {
      "manifest_version": "0.0.2",
      "fwup_bundle": {
        "product": "w3a",
        "from_version": "$fromVersion",
        "to_version": "$toVersion",
        "mcus": {
          "core": {
            "mcu_name": "efr32",
            "assets": {
              "a2b_patch": {
                "image": {"name": "core-a-to-b.patch"},
                "signature": {"name": "core-a-to-b.sig"}
              },
              "b2a_patch": {
                "image": {"name": "core-b-to-a.patch"},
                "signature": {"name": "core-b-to-a.sig"}
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
              "a2b_patch": {
                "image": {"name": "uxc-a-to-b.patch"},
                "signature": {"name": "uxc-a-to-b.sig"}
              },
              "b2a_patch": {
                "image": {"name": "uxc-b-to-a.patch"},
                "signature": {"name": "uxc-b-to-a.sig"}
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

  fileManager.writeFile(manifestJson.encodeToByteArray(), "$dir/fwup-delta-manifest.json")

  // Mock patch and signature files for both directions of both MCUs
  for (mcu in listOf("core", "uxc")) {
    for (dir2 in listOf("a-to-b", "b-to-a")) {
      fileManager.writeFile("patch-$mcu-$dir2".encodeToByteArray(), "$dir/$mcu-$dir2.patch")
      fileManager.writeFile("sig-$mcu-$dir2".encodeToByteArray(), "$dir/$mcu-$dir2.sig")
    }
  }
}

/**
 * No-op downloader — manifest and firmware files are pre-written to [FileManagerFake].
 */
private object NoOpFirmwareDownloader : FirmwareDownloader {
  override suspend fun download(
    deviceInfo: FirmwareDeviceInfo,
  ): Result<Unit, FirmwareDownloadError> = Ok(Unit)
}

private fun fixedFileManagerProvider(fm: FileManager): FileManagerProvider =
  object : FileManagerProvider {
    override fun get(): StateFlow<FileManager> = MutableStateFlow(fm)
  }

private fun fixedDownloaderProvider(dl: FirmwareDownloader): FirmwareDownloaderProvider =
  object : FirmwareDownloaderProvider {
    override fun get(): StateFlow<FirmwareDownloader> = MutableStateFlow(dl)
  }
