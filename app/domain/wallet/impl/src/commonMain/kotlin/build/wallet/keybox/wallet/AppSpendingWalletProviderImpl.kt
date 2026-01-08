package build.wallet.keybox.wallet

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.descriptor.FrostWalletDescriptorFactory
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitcoin.wallet.SpendingWalletDescriptor
import build.wallet.bitcoin.wallet.SpendingWalletProvider
import build.wallet.bitcoin.wallet.WalletV2Provider
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.keybox.SoftwareKeybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.toErrorIfNull

@BitkeyInject(AppScope::class)
class AppSpendingWalletProviderImpl(
  private val spendingWalletProvider: SpendingWalletProvider,
  private val walletV2Provider: WalletV2Provider,
  private val bdk2FeatureFlag: Bdk2FeatureFlag,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val descriptorBuilder: BitcoinMultiSigDescriptorBuilder,
  private val frostWalletDescriptorFactory: FrostWalletDescriptorFactory,
) : AppSpendingWalletProvider {
  override suspend fun getSpendingWallet(
    keyset: SpendingKeyset,
  ): Result<SpendingWallet, Throwable> =
    coroutineBinding {
      val walletDescriptor =
        walletDescriptor(keyset)
          .logFailure { "Error creating wallet descriptor for keyset." }
          .bind()

      if (bdk2FeatureFlag.isEnabled()) {
        walletV2Provider
          .getWallet(walletDescriptor)
          .logFailure { "Error creating v2 wallet for keyset." }
          .bind()
      } else {
        spendingWalletProvider
          .getWallet(walletDescriptor)
          .logFailure { "Error creating wallet for keyset." }
          .bind()
      }
    }

  override suspend fun getSpendingWallet(
    keybox: SoftwareKeybox,
  ): Result<SpendingWallet, Throwable> =
    coroutineBinding {
      val walletDescriptor =
        walletDescriptor(keybox)
          .logFailure { "Error creating wallet descriptor for keybox." }
          .bind()

      if (bdk2FeatureFlag.isEnabled()) {
        walletV2Provider
          .getWallet(walletDescriptor)
          .logFailure { "Error creating v2 wallet for keybox." }
          .bind()
      } else {
        spendingWalletProvider
          .getWallet(walletDescriptor)
          .logFailure { "Error creating wallet for keybox." }
          .bind()
      }
    }

  private suspend fun walletDescriptor(
    keyset: SpendingKeyset,
  ): Result<SpendingWalletDescriptor, Throwable> =
    coroutineBinding {
      val appPrivateSpendingKey = readAppPrivateSpendingKey(keyset.appKey).bind()
      val receivingDescriptor =
        descriptorBuilder.spendingReceivingDescriptor(
          appPrivateKey = appPrivateSpendingKey.key,
          hardwareKey = keyset.hardwareKey.key,
          serverKey = keyset.f8eSpendingKeyset.spendingPublicKey.key
        )
      val changeDescriptor =
        descriptorBuilder.spendingChangeDescriptor(
          appPrivateKey = appPrivateSpendingKey.key,
          hardwareKey = keyset.hardwareKey.key,
          serverKey = keyset.f8eSpendingKeyset.spendingPublicKey.key
        )
      SpendingWalletDescriptor(
        identifier = keyset.localId,
        networkType = keyset.networkType,
        receivingDescriptor = receivingDescriptor,
        changeDescriptor = changeDescriptor
      )
    }

  private suspend fun walletDescriptor(
    keybox: SoftwareKeybox,
  ): Result<SpendingWalletDescriptor, Throwable> =
    coroutineBinding {
      frostWalletDescriptorFactory.spendingWalletDescriptor(
        softwareKeybox = keybox
      )
    }

  private suspend fun readAppPrivateSpendingKey(
    publicKey: AppSpendingPublicKey,
  ): Result<AppSpendingPrivateKey, Throwable> {
    return appPrivateKeyDao.getAppSpendingPrivateKey(publicKey)
      .toErrorIfNull { IllegalStateException("App spending private key not found") }
  }
}
