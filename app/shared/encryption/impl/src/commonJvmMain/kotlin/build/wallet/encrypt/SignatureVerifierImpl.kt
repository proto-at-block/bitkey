package build.wallet.encrypt

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SignatureVerifier.VerifyEcdsaResult
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import build.wallet.rust.core.SignatureVerifier as CoreSignatureVerifier

@BitkeyInject(AppScope::class)
class SignatureVerifierImpl : SignatureVerifier {
  override fun verifyEcdsa(
    message: ByteString,
    signature: String,
    publicKey: Secp256k1PublicKey,
  ): VerifyEcdsaResult {
    CoreSignatureVerifier(signature.decodeHex().toByteArray())
      .verifyEcdsa(
        message.toByteArray(),
        publicKey.value.decodeHex().toByteArray()
      )

    return VerifyEcdsaResult(isValid = true)
  }
}
