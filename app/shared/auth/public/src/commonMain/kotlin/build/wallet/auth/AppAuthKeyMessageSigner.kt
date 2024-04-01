package build.wallet.auth

import build.wallet.bitkey.app.AppAuthKey
import build.wallet.crypto.CurveType
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result
import okio.ByteString

interface AppAuthKeyMessageSigner {
  /**
   * Signs some [message] using private app auth key corresponding to [publicKey].
   *
   * If a private key for [publicKey] doesn't exist, returns an error.
   */
  suspend fun <T> signMessage(
    publicKey: PublicKey<T>,
    message: ByteString,
  ): Result<String, Throwable> where T : AppAuthKey, T : CurveType.Secp256K1
}
