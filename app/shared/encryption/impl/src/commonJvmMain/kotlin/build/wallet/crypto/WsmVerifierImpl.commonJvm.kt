package build.wallet.crypto

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.rust.core.WsmIntegrityVerifier

@BitkeyInject(AppScope::class)
class WsmVerifierImpl : WsmVerifier {
  override fun verify(
    base58Message: String,
    signature: String,
    keyVariant: WsmIntegrityKeyVariant,
  ): WsmVerifierResult {
    return WsmVerifierResult(
      WsmIntegrityVerifier(keyVariant.pubkey).verify(
        base58Message = base58Message,
        signature = signature
      )
    )
  }
}
