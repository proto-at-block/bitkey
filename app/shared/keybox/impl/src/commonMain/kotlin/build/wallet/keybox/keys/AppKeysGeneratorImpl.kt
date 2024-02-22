package build.wallet.keybox.keys

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppGlobalAuthPrivateKey
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthKeypair
import build.wallet.bitkey.app.AppRecoveryAuthPrivateKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.logging.logFailure
import build.wallet.platform.random.Uuid
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding

class AppKeysGeneratorImpl(
  private val uuid: Uuid,
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
        localId = uuid.random(),
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

  override suspend fun generateGlobalAuthKey(): Result<AppGlobalAuthPublicKey, Throwable> =
    binding {
      val authKeypair = appAuthKeyGenerator.generateGlobalAuthKey().bind()
      val appGlobalAuthKeypair =
        AppGlobalAuthKeypair(
          publicKey = AppGlobalAuthPublicKey(authKeypair.publicKey.pubKey),
          privateKey = AppGlobalAuthPrivateKey(authKeypair.privateKey.key)
        )
      appPrivateKeyDao.storeAppAuthKeyPair(appGlobalAuthKeypair).bind()

      appGlobalAuthKeypair.publicKey
    }.logFailure { "Error generating new global app authentication key" }

  override suspend fun generateRecoveryAuthKey(): Result<AppRecoveryAuthPublicKey, Throwable> =
    binding {
      val authKeypair = appAuthKeyGenerator.generateRecoveryAuthKey().bind()
      val appRecoveryAuthKeypair =
        AppRecoveryAuthKeypair(
          publicKey = AppRecoveryAuthPublicKey(authKeypair.publicKey.pubKey),
          privateKey = AppRecoveryAuthPrivateKey(authKeypair.privateKey.key)
        )
      appPrivateKeyDao.storeAppAuthKeyPair(appRecoveryAuthKeypair).bind()

      appRecoveryAuthKeypair.publicKey
    }.logFailure { "Error generating new recovery app authentication key" }
}
