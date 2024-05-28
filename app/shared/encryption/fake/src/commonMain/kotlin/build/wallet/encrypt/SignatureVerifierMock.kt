package build.wallet.encrypt

import okio.ByteString

class SignatureVerifierMock : SignatureVerifier {
  override fun verifyEcdsa(
    message: ByteString,
    signature: String,
    publicKey: Secp256k1PublicKey,
  ): SignatureVerifier.VerifyEcdsaResult {
    return SignatureVerifier.VerifyEcdsaResult(isValid = true)
  }
}
