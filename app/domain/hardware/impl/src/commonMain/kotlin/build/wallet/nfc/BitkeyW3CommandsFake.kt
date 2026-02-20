package build.wallet.nfc

import build.wallet.Progress
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.spending.SpendingKeyset
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
    version: String,
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
   * W3 hardware generates address from stored descriptor and displays it on screen.
   *
   * The address verification UI is handled at the app layer, not the NFC command layer.
   * This command simply returns the address - the user visually confirms it matches
   * what's displayed on their hardware device.
   */
  override suspend fun getAddress(
    session: NfcSession,
    addressIndex: UInt,
  ): String = "bc1q_fake_w3_$addressIndex"

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
      hwRevision = "w3a-core-evt"
    )

  /**
   * W3 hardware requires chunked PSBT transfer and on-device confirmation for transaction signing.
   *
   * Returns [HardwareInteraction.RequiresTransfer] to simulate the W3's chunked transfer flow:
   * 1. Transfer PSBT data (instantaneous in fake)
   * 2. Show emulated confirmation prompt (simulating device screen)
   * 3. After user approval, return signed PSBT on second tap
   */
  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
  ): HardwareInteraction<Psbt> {
    // W3 returns RequiresTransfer to indicate chunked transfer is needed
    return HardwareInteraction.RequiresTransfer { _, _, onProgress ->
      // Fake transfer completes immediately
      onProgress(Progress.Full)

      // After transfer, return emulated prompt for user confirmation
      // The fake's fetchResult simulates the entire confirmation flow
      HardwareInteraction.ConfirmWithEmulatedPrompt(
        options = listOf(
          EmulatedPromptOption(
            name = EmulatedPromptOption.APPROVE,
            fetchResult = { fetchSession, _ ->
              // Fake simulates the device signing the PSBT
              // The real code calls getConfirmationResult + getConfirmationResultChunk,
              // but the fake shortcuts this by returning the final result directly
              val w1Result = w1CommandsFake.signTransaction(fetchSession, psbt, spendingKeyset)
              when (w1Result) {
                is HardwareInteraction.Completed -> w1Result
                else -> throw NfcException.CommandError("Unexpected interaction type from W1 fake")
              }
            }
          ),
          EmulatedPromptOption(
            name = EmulatedPromptOption.DENY,
            fetchResult = { _, _ ->
              throw NfcException.CommandError("User denied transaction")
            }
          )
        )
      )
    }
  }

  /**
   * W3 hardware verifies keys and builds the hardware descriptor.
   *
   * This fake implementation simulates successful verification and descriptor building.
   * In a real device, this would:
   * 1. Verify the app and server keys match hardware expectations
   * 2. Verify the WSM signature over all public keys
   * 3. Store the descriptor in hardware for future use
   */
  override suspend fun verifyKeysAndBuildDescriptor(
    session: NfcSession,
    appSpendingKey: ByteString,
    appSpendingKeyChaincode: ByteString,
    networkMainnet: Boolean,
    appAuthKey: ByteString,
    serverSpendingKey: ByteString,
    serverSpendingKeyChaincode: ByteString,
    wsmSignature: ByteString,
  ): Boolean = true
}

/**
 * Fake firmware device info for W3 hardware.
 * Key differences from W1:
 * - hwRevision uses W3 format: "w3a-core-evt" (product-mcu-stage)
 * - mcuInfo populated with CORE and UXC MCUs for multi-MCU FWUP support
 *
 * Note: Uses the same serial as FakeFirmwareDeviceInfo ("fake-serial") so that
 * FirmwareDataServiceImpl.syncLatestFwupData() skips firmware downloads in tests.
 */
val FakeW3FirmwareDeviceInfo = FirmwareDeviceInfo(
  version = "1.2.3",
  serial = FakeFirmwareDeviceInfo.serial,
  swType = "dev",
  hwRevision = "w3a-core-evt",
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
