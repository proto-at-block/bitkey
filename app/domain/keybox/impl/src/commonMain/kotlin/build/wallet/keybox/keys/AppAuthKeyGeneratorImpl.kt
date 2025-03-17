package build.wallet.keybox.keys

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.Secp256k1KeyGenerator
import build.wallet.encrypt.toPrivateKey
import build.wallet.encrypt.toPublicKey
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding

@BitkeyInject(AppScope::class)
class AppAuthKeyGeneratorImpl(
  private val secp256k1KeyGenerator: Secp256k1KeyGenerator,
) : AppAuthKeyGenerator {
  override suspend fun generateGlobalAuthKey(): Result<AppKey<AppGlobalAuthKey>, Throwable> =
    binding {
      val secp256k1Keypair = catchingResult { secp256k1KeyGenerator.generateKeypair() }.bind()

      AppKey(
        publicKey = secp256k1Keypair.publicKey.toPublicKey(),
        privateKey = secp256k1Keypair.privateKey.toPrivateKey()
      )
    }

  override suspend fun generateRecoveryAuthKey(): Result<AppKey<AppRecoveryAuthKey>, Throwable> =
    binding {
      val secp256k1Keypair = catchingResult { secp256k1KeyGenerator.generateKeypair() }.bind()

      AppKey(
        publicKey = secp256k1Keypair.publicKey.toPublicKey(),
        privateKey = secp256k1Keypair.privateKey.toPrivateKey()
      )
    }
}
