package build.wallet.nfc

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import build.wallet.nfc.platform.NfcCommands

@BitkeyInject(AppScope::class)
class NfcCommandsProvider(
  @Impl private val impl: NfcCommands,
  @Fake private val fake: NfcCommands,
) {
  operator fun invoke(parameters: NfcSession.Parameters): NfcCommands =
    when (parameters.isHardwareFake) {
      true -> fake
      false -> impl
    }
}
