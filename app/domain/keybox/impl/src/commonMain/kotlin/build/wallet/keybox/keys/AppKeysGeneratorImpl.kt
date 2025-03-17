package build.wallet.keybox.keys

import bitkey.account.AccountConfigService
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.ExtendedKeyGenerator
import build.wallet.bitkey.app.*
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class AppKeysGeneratorImpl(
  private val uuidGenerator: UuidGenerator,
  private val extendedKeyGenerator: ExtendedKeyGenerator,
  private val appAuthKeyGenerator: AppAuthKeyGenerator,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val accountConfigService: AccountConfigService,
) : AppKeysGenerator {
  override suspend fun generateKeyBundle(): Result<AppKeyBundle, Throwable> =
    coroutineBinding {
      val bitcoinNetwork = accountConfigService.activeOrDefaultConfig().value.bitcoinNetworkType
      val appSpendingPublicKey = generateAppSpendingKey(bitcoinNetwork).bind()
      val appAuthPublicKey = generateGlobalAuthKey().bind()
      AppKeyBundle(
        localId = uuidGenerator.random(),
        spendingKey = appSpendingPublicKey,
        authKey = appAuthPublicKey,
        networkType = bitcoinNetwork,
        recoveryAuthKey = generateRecoveryAuthKey().bind()
      )
    }

  private suspend fun generateAppSpendingKey(
    network: BitcoinNetworkType,
  ): Result<AppSpendingPublicKey, Throwable> =
    coroutineBinding {
      val keypair = extendedKeyGenerator.generate(network).bind()
      val appSpendingKeypair = AppSpendingKeypair(
        publicKey = AppSpendingPublicKey(keypair.publicKey),
        privateKey = AppSpendingPrivateKey(keypair.privateKey)
      )
      appPrivateKeyDao.storeAppSpendingKeyPair(appSpendingKeypair).bind()

      appSpendingKeypair.publicKey
    }.logFailure { "Error generating new app spending key" }

  override suspend fun generateGlobalAuthKey(): Result<PublicKey<AppGlobalAuthKey>, Throwable> =
    coroutineBinding {
      val authKeypair = appAuthKeyGenerator.generateGlobalAuthKey().bind()
      appPrivateKeyDao.storeAppKeyPair(authKeypair).bind()

      authKeypair.publicKey
    }.logFailure { "Error generating new app global auth key" }

  override suspend fun generateRecoveryAuthKey(): Result<PublicKey<AppRecoveryAuthKey>, Throwable> =
    coroutineBinding {
      val authKeypair = appAuthKeyGenerator.generateRecoveryAuthKey().bind()
      appPrivateKeyDao.storeAppKeyPair(authKeypair).bind()

      authKeypair.publicKey
    }.logFailure { "Error generating new recovery app auth key" }
}
