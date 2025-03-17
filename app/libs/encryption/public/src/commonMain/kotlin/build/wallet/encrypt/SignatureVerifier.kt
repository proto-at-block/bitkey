package build.wallet.encrypt

import build.wallet.catchingResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import okio.ByteString

interface SignatureVerifier {
  /**
   * Verifies an ECDSA signature.
   *
   * Prefer using [verifyEcdsaResult] instead of this method, as it returns a [Result] instead of
   * throwing an exception at runtime.
   */
  @Throws(Throwable::class)
  fun verifyEcdsa(
    message: ByteString,
    signature: String,
    publicKey: Secp256k1PublicKey,
  ): VerifyEcdsaResult

  /**
   * A workaround hack to fix a Kotlin - ObjC interop issue:
   * "Throwing method cannot be an implementation of an @objc requirement because it returns a
   * value of type 'Bool'; return 'Void' or a type that bridges to an Objective-C class"
   *
   * TODO(BKR-1022): remove this workaround and return Boolean straight up.
   */
  data class VerifyEcdsaResult(
    val isValid: Boolean,
  )
}

/**
 * Non throwing version of [SignatureVerifier.verifyEcdsa].
 */
fun SignatureVerifier.verifyEcdsaResult(
  message: ByteString,
  signature: String,
  publicKey: Secp256k1PublicKey,
): Result<Boolean, SignatureVerifierError> =
  catchingResult { verifyEcdsa(message, signature, publicKey) }
    .map { it.isValid }
    .mapError(::SignatureVerifierError)

data class SignatureVerifierError(override val cause: Throwable) : Error()
