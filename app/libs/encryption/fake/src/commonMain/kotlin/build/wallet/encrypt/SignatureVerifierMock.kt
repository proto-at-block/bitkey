package build.wallet.encrypt

import app.cash.turbine.Turbine
import okio.ByteString

class SignatureVerifierMock(
  val verifyCallsTurbine: Turbine<VerifyEcdsaCall>? = null,
) : SignatureVerifier {
  var isValid = true

  override fun verifyEcdsa(
    message: ByteString,
    signature: String,
    publicKey: Secp256k1PublicKey,
  ): SignatureVerifier.VerifyEcdsaResult {
    verifyCallsTurbine?.add(VerifyEcdsaCall(message, signature, publicKey))
    return SignatureVerifier.VerifyEcdsaResult(isValid = isValid)
  }

  fun reset() {
    isValid = true
  }

  data class VerifyEcdsaCall(
    val message: ByteString,
    val signature: String,
    val publicKey: Secp256k1PublicKey,
  )
}
