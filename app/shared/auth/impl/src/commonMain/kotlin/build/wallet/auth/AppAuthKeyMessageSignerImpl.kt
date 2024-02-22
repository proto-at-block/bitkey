package build.wallet.auth

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppAuthPublicKey
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.signResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.toErrorIfNull
import okio.ByteString

class AppAuthKeyMessageSignerImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val messageSigner: MessageSigner,
) : AppAuthKeyMessageSigner {
  override suspend fun signMessage(
    publicKey: AppAuthPublicKey,
    message: ByteString,
  ): Result<String, Throwable> {
    return binding {
      val privateKeyResult =
        when (publicKey) {
          is AppGlobalAuthPublicKey -> appPrivateKeyDao.getGlobalAuthKey(publicKey)
          is AppRecoveryAuthPublicKey -> appPrivateKeyDao.getRecoveryAuthKey(publicKey)
        }

      val privateKey =
        privateKeyResult
          .toErrorIfNull { AppAuthKeyMissingError }
          .bind()

      messageSigner
        .signResult(message, privateKey.key)
        .bind()
    }
  }
}

object AppAuthKeyMissingError : Error()
