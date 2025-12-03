package build.wallet.nfc

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.SecureBootConfig
import build.wallet.nfc.platform.NfcCommands
import kotlinx.datetime.Instant
import okio.ByteString

/**
 * Fake implementation of NFC commands for the W3.
 *
 * Unless explicitly overridden here this will delegate to the fake W1 commands.
 * Note: Does not use @Fake qualifier to avoid conflict with W1 fake in DI system.
 */
@BitkeyInject(AppScope::class)
class BitkeyW3CommandsFake(
  private val w1CommandsFake: BitkeyW1CommandsFake,
) : NfcCommands by w1CommandsFake {
  /**
   * Override to return W3-specific device info with W3 hardware revision.
   */
  override suspend fun getDeviceInfo(session: NfcSession) = FakeW3FirmwareDeviceInfo

  /**
   * Override to return W3-specific firmware metadata with W3 hardware revision.
   */
  override suspend fun getFirmwareMetadata(session: NfcSession) =
    FirmwareMetadata(
      activeSlot = FirmwareMetadata.FirmwareSlot.A,
      gitId = "some-fake-w3-id",
      gitBranch = "main",
      version = "1.0",
      build = "mock-w3",
      timestamp = Instant.DISTANT_PAST,
      hash = ByteString.EMPTY,
      hwRevision = "w3a-evt"
    )
}

/**
 * Fake firmware device info for W3 hardware.
 * Key difference from W1: hwRevision starts with "w3" for W3 hardware detection.
 */
val FakeW3FirmwareDeviceInfo = FirmwareDeviceInfo(
  version = "1.2.3",
  serial = "fake-w3-serial",
  swType = "dev",
  hwRevision = "w3a-evt",
  activeSlot = FirmwareMetadata.FirmwareSlot.B,
  batteryCharge = 89.45,
  vCell = 1000,
  avgCurrentMa = 1234,
  batteryCycles = 1234,
  secureBootConfig = SecureBootConfig.PROD,
  timeRetrieved = 1691787589,
  bioMatchStats = null
)
