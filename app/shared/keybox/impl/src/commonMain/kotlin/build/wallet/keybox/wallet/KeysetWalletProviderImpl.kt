package build.wallet.keybox.wallet

import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.bitcoin.wallet.WatchingWalletDescriptor
import build.wallet.bitcoin.wallet.WatchingWalletProvider
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding

class KeysetWalletProviderImpl(
  private val watchingWalletProvider: WatchingWalletProvider,
  private val descriptorBuilder: BitcoinMultiSigDescriptorBuilder,
) : KeysetWalletProvider {
  override suspend fun getWatchingWallet(
    keyset: SpendingKeyset,
  ): Result<WatchingWallet, Throwable> =
    binding {
      val walletDescriptor =
        walletDescriptor(keyset)
          .logFailure { "Error creating watching wallet descriptor for keyset." }
          .bind()

      watchingWalletProvider
        .getWallet(walletDescriptor)
        .logFailure { "Error creating watching wallet for keyset." }
        .bind()
    }

  private suspend fun walletDescriptor(
    keyset: SpendingKeyset,
  ): Result<WatchingWalletDescriptor, Throwable> =
    binding {
      val receivingDescriptor =
        descriptorBuilder.watchingReceivingDescriptor(
          appPublicKey = keyset.appKey.key,
          hardwareKey = keyset.hardwareKey.key,
          serverKey = keyset.f8eSpendingKeyset.spendingPublicKey.key
        )
      val changeDescriptor =
        descriptorBuilder.watchingChangeDescriptor(
          appPublicKey = keyset.appKey.key,
          hardwareKey = keyset.hardwareKey.key,
          serverKey = keyset.f8eSpendingKeyset.spendingPublicKey.key
        )
      WatchingWalletDescriptor(
        identifier = keyset.localId,
        networkType = keyset.networkType,
        receivingDescriptor = receivingDescriptor,
        changeDescriptor = changeDescriptor
      )
    }
}
