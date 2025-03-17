package build.wallet.nfc

import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.nfc.platform.NfcSessionProvider
import kotlinx.coroutines.CoroutineScope

@BitkeyInject(ActivityScope::class)
class NfcSessionProviderImpl(
  private val nfcTagScanner: NfcTagScanner,
  private val appCoroutineScope: CoroutineScope,
) : NfcSessionProvider {
  override fun get(parameters: NfcSession.Parameters): NfcSession =
    when (parameters.isHardwareFake) {
      true -> NfcSessionFake(parameters)
      false ->
        NfcSessionImpl(
          parameters,
          nfcTagScanner,
          appCoroutineScope
        )
    }
}
