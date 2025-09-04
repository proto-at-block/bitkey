package build.wallet.encrypt

import build.wallet.crypto.WsmIntegrityKeyVariant
import build.wallet.crypto.WsmVerifier
import build.wallet.crypto.WsmVerifierResult

class WsmVerifierMock : WsmVerifier {
  override fun verify(
    base58Message: String,
    signature: String,
    keyVariant: WsmIntegrityKeyVariant,
  ): WsmVerifierResult = WsmVerifierResult(true)

  override fun verifyHexMessage(
    hexMessage: String,
    signature: String,
    keyVariant: WsmIntegrityKeyVariant,
  ): WsmVerifierResult = WsmVerifierResult(true)
}
