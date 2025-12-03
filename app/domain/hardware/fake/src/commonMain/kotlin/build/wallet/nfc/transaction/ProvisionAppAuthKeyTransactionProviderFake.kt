package build.wallet.nfc.transaction

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey

class ProvisionAppAuthKeyTransactionProviderFake : ProvisionAppAuthKeyTransactionProvider {
  var invoked = false
  var lastAppGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>? = null

  override fun invoke(
    appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
  ): NfcTransaction<Unit> {
    invoked = true
    lastAppGlobalAuthPublicKey = appGlobalAuthPublicKey
    return object : NfcTransaction<Unit> {
      override val needsAuthentication = true
      override val shouldLock = true

      override suspend fun session(
        session: build.wallet.nfc.NfcSession,
        commands: build.wallet.nfc.platform.NfcCommands,
      ) {
        // Fake implementation - no-op
      }

      override fun onCancel() = onCancel()

      override suspend fun onSuccess(response: Unit) = onSuccess()
    }
  }

  fun reset() {
    invoked = false
    lastAppGlobalAuthPublicKey = null
  }
}
