package build.wallet.keybox.keys

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.catchingResult
import build.wallet.encrypt.Secp256k1KeyGenerator
import build.wallet.encrypt.toPrivateKey
import build.wallet.encrypt.toPublicKey
import build.wallet.logging.log
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding

class AppAuthKeyGeneratorImpl(
  private val secp256k1KeyGenerator: Secp256k1KeyGenerator,
) : AppAuthKeyGenerator {
  override suspend fun generateGlobalAuthKey(): Result<AppKey<AppGlobalAuthKey>, Throwable> =
    binding {
      log { "Generating app global auth key" }

      val secp256k1Keypair = catchingResult { secp256k1KeyGenerator.generateKeypair() }.bind()

      AppKey(
        publicKey = secp256k1Keypair.publicKey.toPublicKey(),
        privateKey = secp256k1Keypair.privateKey.toPrivateKey()
      )
    }

  override suspend fun generateRecoveryAuthKey(): Result<AppKey<AppRecoveryAuthKey>, Throwable> =
    binding {
      log { "Generating app recovery auth key" }

      val secp256k1Keypair = catchingResult { secp256k1KeyGenerator.generateKeypair() }.bind()

      AppKey(
        publicKey = secp256k1Keypair.publicKey.toPublicKey(),
        privateKey = secp256k1Keypair.privateKey.toPrivateKey()
      )
    }
}
