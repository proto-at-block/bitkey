package build.wallet.keybox.keys

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.logging.logFailure
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding

class AppKeysGeneratorImpl(
  private val uuidGenerator: UuidGenerator,
  private val spendingKeyGenerator: SpendingKeyGenerator,
  private val appAuthKeyGenerator: AppAuthKeyGenerator,
  private val appPrivateKeyDao: AppPrivateKeyDao,
) : AppKeysGenerator {
  override suspend fun generateKeyBundle(
    network: BitcoinNetworkType,
  ): Result<AppKeyBundle, Throwable> =
    binding {
      val appSpendingPublicKey = generateAppSpendingKey(network).bind()
      val appAuthPublicKey = generateGlobalAuthKey().bind()
      AppKeyBundle(
        localId = uuidGenerator.random(),
        spendingKey = appSpendingPublicKey,
        authKey = appAuthPublicKey,
        networkType = network,
        recoveryAuthKey = generateRecoveryAuthKey().bind()
      )
    }

  private suspend fun generateAppSpendingKey(
    network: BitcoinNetworkType,
  ): Result<AppSpendingPublicKey, Throwable> =
    binding {
      val spendingKeypair = spendingKeyGenerator.generate(network).bind()
      val appSpendingKeypair =
        AppSpendingKeypair(
          publicKey = AppSpendingPublicKey(spendingKeypair.publicKey.key),
          privateKey = AppSpendingPrivateKey(spendingKeypair.privateKey.key)
        )
      appPrivateKeyDao.storeAppSpendingKeyPair(appSpendingKeypair).bind()

      appSpendingKeypair.publicKey
    }.logFailure { "Error generating new app spending key" }

  override suspend fun generateGlobalAuthKey(): Result<PublicKey<AppGlobalAuthKey>, Throwable> =
    binding {
      val authKeypair = appAuthKeyGenerator.generateGlobalAuthKey().bind()
      appPrivateKeyDao.storeAppKeyPair(authKeypair).bind()

      authKeypair.publicKey
    }.logFailure { "Error generating new app global auth key" }

  override suspend fun generateRecoveryAuthKey(): Result<PublicKey<AppRecoveryAuthKey>, Throwable> =
    binding {
      val authKeypair = appAuthKeyGenerator.generateRecoveryAuthKey().bind()
      appPrivateKeyDao.storeAppKeyPair(authKeypair).bind()

      authKeypair.publicKey
    }.logFailure { "Error generating new recovery app auth key" }
}
