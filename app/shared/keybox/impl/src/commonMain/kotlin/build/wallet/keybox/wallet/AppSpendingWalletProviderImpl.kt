package build.wallet.keybox.wallet

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitcoin.wallet.SpendingWalletDescriptor
import build.wallet.bitcoin.wallet.SpendingWalletProvider
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.toErrorIfNull

class AppSpendingWalletProviderImpl(
  private val spendingWalletProvider: SpendingWalletProvider,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val descriptorBuilder: BitcoinMultiSigDescriptorBuilder,
) : AppSpendingWalletProvider {
  override suspend fun getSpendingWallet(
    keyset: SpendingKeyset,
  ): Result<SpendingWallet, Throwable> =
    binding {
      val walletDescriptor =
        walletDescriptor(keyset)
          .logFailure { "Error creating wallet descriptor for keyset." }
          .bind()

      spendingWalletProvider
        .getWallet(walletDescriptor)
        .logFailure { "Error creating wallet for keyset." }
        .bind()
    }

  private suspend fun walletDescriptor(
    keyset: SpendingKeyset,
  ): Result<SpendingWalletDescriptor, Throwable> =
    binding {
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

  private suspend fun readAppPrivateSpendingKey(
    publicKey: AppSpendingPublicKey,
  ): Result<AppSpendingPrivateKey, Throwable> {
    return appPrivateKeyDao.getAppSpendingPrivateKey(publicKey)
      .toErrorIfNull { IllegalStateException("App spending private key not found") }
  }
}
