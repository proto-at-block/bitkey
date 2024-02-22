package build.wallet.keybox.keys

import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppGlobalAuthPrivateKey
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthKeypair
import build.wallet.bitkey.app.AppRecoveryAuthPrivateKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.catching
import build.wallet.encrypt.Secp256k1KeyGenerator
import build.wallet.logging.log
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding

class AppAuthKeyGeneratorImpl(
  private val secp256k1KeyGenerator: Secp256k1KeyGenerator,
) : AppAuthKeyGenerator {
  override suspend fun generateGlobalAuthKey(): Result<AppGlobalAuthKeypair, Throwable> =
    binding {
      log { "Generating app global auth key" }

      val secp256k1Keypair = Result.catching { secp256k1KeyGenerator.generateKeypair() }.bind()

      AppGlobalAuthKeypair(
        publicKey = AppGlobalAuthPublicKey(secp256k1Keypair.publicKey),
        privateKey = AppGlobalAuthPrivateKey(secp256k1Keypair.privateKey)
      )
    }

  override suspend fun generateRecoveryAuthKey(): Result<AppRecoveryAuthKeypair, Throwable> =
    binding {
      log { "Generating app recovery auth key" }

      val secp256k1Keypair = Result.catching { secp256k1KeyGenerator.generateKeypair() }.bind()

      AppRecoveryAuthKeypair(
        publicKey = AppRecoveryAuthPublicKey(secp256k1Keypair.publicKey),
        privateKey = AppRecoveryAuthPrivateKey(secp256k1Keypair.privateKey)
      )
    }
}
