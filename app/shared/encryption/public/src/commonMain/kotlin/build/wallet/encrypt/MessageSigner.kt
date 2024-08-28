package build.wallet.encrypt

import build.wallet.catchingResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString

interface MessageSigner {
  /**
   * Take in an *unhashed* message to be signed. This message will subsequently
   * be hashed underneath the hood with SHA-256, prior to signing.
   *
   * Prefer to use non-throwing version of this binding - [MessageSigner.signResult].
   */
  @Throws(Throwable::class)
  fun sign(
    message: ByteString,
    key: Secp256k1PrivateKey,
  ): String
}

/**
 * Non throwing version of [MessageSigner.sign].
 */
suspend fun MessageSigner.signResult(
  message: ByteString,
  key: Secp256k1PrivateKey,
): Result<String, SignMessageError> {
  return withContext(Dispatchers.Default) {
    catchingResult {
      sign(message, key)
    }.mapError { exception -> SignMessageError.UnhandledException(exception) }
  }
}

sealed class SignMessageError : Error() {
  data object InvalidSecret : SignMessageError()

  data class UnhandledException(override val cause: Throwable) : SignMessageError()
}
