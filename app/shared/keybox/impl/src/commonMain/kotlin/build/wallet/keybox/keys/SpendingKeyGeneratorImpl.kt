package build.wallet.keybox.keys

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.ExtendedKeyGenerator
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeypair
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class SpendingKeyGeneratorImpl(
  private val extendedKeyGenerator: ExtendedKeyGenerator,
) : SpendingKeyGenerator {
  override suspend fun generate(network: BitcoinNetworkType): Result<SpendingKeypair, Throwable> =
    coroutineBinding {
      val spendingDescriptorKeypair = extendedKeyGenerator.generate(network).bind()
      SpendingKeypair(
        publicKey = AppSpendingPublicKey(spendingDescriptorKeypair.publicKey),
        privateKey = AppSpendingPrivateKey(spendingDescriptorKeypair.privateKey)
      )
    }
}
