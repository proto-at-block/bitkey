package build.wallet.nfc

import bitkey.account.HardwareType
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Impl
import build.wallet.di.W3
import build.wallet.nfc.platform.NfcCommands

@BitkeyInject(AppScope::class)
class NfcCommandsProvider(
  @Impl private val w1Impl: NfcCommands,
  @W3 private val w3Impl: NfcCommands,
  private val w1Fake: BitkeyW1CommandsFake,
  private val w3Fake: BitkeyW3CommandsFake,
) {
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
