package build.wallet.auth

import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.challange.DelayNotifyChallenge
import build.wallet.bitkey.challange.SignedChallenge.AppSignedChallenge
import build.wallet.crypto.CurveType
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
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

/**
 * Use a message signer to sign a delay/notify challenge.
 */
suspend fun <T : AppAuthKey> AppAuthKeyMessageSigner.signChallenge(
  publicKey: PublicKey<T>,
  challenge: DelayNotifyChallenge,
): Result<AppSignedChallenge, Throwable> {
  return signMessage(
    publicKey = publicKey,
    message = challenge.asByteString()
  ).map { AppSignedChallenge(challenge, it) }
}
