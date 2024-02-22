package build.wallet.auth

import build.wallet.bitkey.app.AppAuthPublicKey
import com.github.michaelbull.result.Result
import okio.ByteString

interface AppAuthKeyMessageSigner {
  /**
   * Signs some [message] using private app auth key corresponding to [publicKey].
   *
   * If a private key for [publicKey] doesn't exist, returns an error.
   */
  suspend fun signMessage(
    publicKey: AppAuthPublicKey,
    message: ByteString,
  ): Result<String, Throwable>
}
