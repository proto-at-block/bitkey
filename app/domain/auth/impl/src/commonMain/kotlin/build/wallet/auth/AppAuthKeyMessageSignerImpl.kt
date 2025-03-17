package build.wallet.auth

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.crypto.CurveType
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.signResult
import build.wallet.encrypt.toSecp256k1PrivateKey
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.toErrorIfNull
import okio.ByteString

@BitkeyInject(AppScope::class)
class AppAuthKeyMessageSignerImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val messageSigner: MessageSigner,
) : AppAuthKeyMessageSigner {
  override suspend fun <T> signMessage(
    publicKey: PublicKey<T>,
    message: ByteString,
  ): Result<String, Throwable> where T : AppAuthKey, T : CurveType.Secp256K1 {
    return coroutineBinding {
      val privateKeyResult = appPrivateKeyDao.getAsymmetricPrivateKey(publicKey)

      val privateKey =
        privateKeyResult
          .toErrorIfNull { AppAuthKeyMissingError }
          .bind()

      messageSigner
        .signResult(message, privateKey.toSecp256k1PrivateKey())
        .bind()
    }
  }
}

object AppAuthKeyMissingError : Error()
