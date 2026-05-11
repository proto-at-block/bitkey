package build.wallet.nfc

import bitkey.account.HardwareType
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Impl
import build.wallet.di.W3
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.W3NfcCommands

@BitkeyInject(AppScope::class)
class NfcCommandsProvider(
  @Impl private val w1Impl: NfcCommands,
  @W3 private val w3Impl: W3NfcCommands,
  private val w1Fake: BitkeyW1CommandsFake,
  private val w3Fake: BitkeyW3CommandsFake,
) {
  /**
   * Returns the W1 base commands suitable for pre-authentication calls like [getDeviceInfo].
   */
  fun baseCommands(isHardwareFake: Boolean): NfcCommands =
    if (isHardwareFake) w1Fake else w1Impl

  /**
   * Returns session-scoped commands. Real hardware uses auto-detect routing, optionally seeded
   * with previously resolved device identity for continuation taps; fakes trust params.
   */
  fun forSession(parameters: NfcSession.Parameters): NfcCommands =
    if (parameters.isHardwareFake) {
      invoke(parameters)
    } else {
      ResolvingNfcCommands(
        baseCommands = baseCommands(isHardwareFake = false),
        commandsProvider = this,
        initialDeviceInfo = parameters.resolvedDeviceInfoOverride
      )
    }

  /**
   * Returns the correct commands implementation for the given [HardwareType].
   */
  fun forHardwareType(type: HardwareType, isHardwareFake: Boolean): NfcCommands =
    if (isHardwareFake) {
      when (type) {
        HardwareType.W1 -> w1Fake
        HardwareType.W3 -> w3Fake
      }
    } else {
      when (type) {
        HardwareType.W1 -> w1Impl
        HardwareType.W3 -> w3Impl
      }
    }

  operator fun invoke(parameters: NfcSession.Parameters): NfcCommands {
    return if (parameters.isHardwareFake) {
      when (parameters.hardwareType) {
        HardwareType.W1, null -> w1Fake
        HardwareType.W3 -> w3Fake
      }
    } else {
      when (parameters.hardwareType) {
        HardwareType.W1, null -> w1Impl
        HardwareType.W3 -> w3Impl
      }
    }
  }
}
