package build.wallet.crypto

import build.wallet.core.WsmIntegrityVerifier

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
