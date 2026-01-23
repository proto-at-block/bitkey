package build.wallet.nfc

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.McuInfo
import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import build.wallet.firmware.SecureBootConfig
import build.wallet.nfc.platform.EmulatedPromptOption
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import kotlinx.datetime.Instant
import okio.ByteString

/**
 * Fake implementation of NFC commands for the W3.
 *
 * Delegates to W1 fake commands unless overridden.
 */
@BitkeyInject(AppScope::class)
class BitkeyW3CommandsFake(
  private val w1CommandsFake: BitkeyW1CommandsFake,
) : NfcCommands by w1CommandsFake {
  /**
   * W3 hardware requires on-device confirmation for wipe operations.
   *
   * Returns [HardwareInteraction.ConfirmWithEmulatedPrompt] to simulate the device's
   * confirmation screen. When "Approve" is selected, [EmulatedPromptOption.onSelect]
   * wipes the fake device state.
   */
  override suspend fun wipeDevice(session: NfcSession): HardwareInteraction<Boolean> {
    return HardwareInteraction.ConfirmWithEmulatedPrompt(
      options = listOf(
        EmulatedPromptOption(
          name = EmulatedPromptOption.APPROVE,
          onSelect = { w1CommandsFake.wipeDevice() },
          fetchResult = { _, _ ->
            // Simulates real device behavior: wipe invalidates the session nonce,
            // so the second NFC tap fails because the device no longer recognizes it.
            throw NfcException.CommandError("Unknown nonce - device was wiped")
          }
        ),
        EmulatedPromptOption(
          name = EmulatedPromptOption.DENY,
          fetchResult = { _, _ -> HardwareInteraction.Completed(false) }
        )
      )
    )
  }

  /**
   * W3 hardware requires on-device confirmation for firmware update operations.
   *
   * Returns [HardwareInteraction.ConfirmWithEmulatedPrompt] to simulate the device's
   * confirmation screen before starting the FWUP process.
   */
  override suspend fun fwupStart(
    session: NfcSession,
    patchSize: UInt?,
    fwupMode: build.wallet.fwup.FwupMode,
    mcuRole: build.wallet.firmware.McuRole,
  ): HardwareInteraction<Boolean> {
    return HardwareInteraction.ConfirmWithEmulatedPrompt(
      options = listOf(
        EmulatedPromptOption(
          name = EmulatedPromptOption.APPROVE,
          fetchResult = { _, _ -> HardwareInteraction.Completed(true) }
        ),
        EmulatedPromptOption(
          name = EmulatedPromptOption.DENY,
          fetchResult = { _, _ -> HardwareInteraction.Completed(false) }
        )
      )
    )
  }

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
 * Key differences from W1:
 * - hwRevision starts with "w3" for W3 hardware detection
 * - mcuInfo populated with CORE and UXC MCUs for multi-MCU FWUP support
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
  bioMatchStats = null,
  mcuInfo = listOf(
    McuInfo(
      mcuRole = McuRole.CORE,
      mcuName = McuName.EFR32,
      firmwareVersion = "1.2.3"
    ),
    McuInfo(
      mcuRole = McuRole.UXC,
      mcuName = McuName.STM32U5,
      firmwareVersion = "1.2.3"
    )
  )
)
