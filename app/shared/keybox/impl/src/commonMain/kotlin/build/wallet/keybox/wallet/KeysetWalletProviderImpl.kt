package build.wallet.keybox.wallet

import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.bitcoin.wallet.WatchingWalletProvider
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

class KeysetWalletProviderImpl(
  private val watchingWalletProvider: WatchingWalletProvider,
  private val watchingWalletDescriptorProvider: WatchingWalletDescriptorProvider,
) : KeysetWalletProvider {
  override suspend fun getWatchingWallet(
    keyset: SpendingKeyset,
  ): Result<WatchingWallet, Throwable> =
    coroutineBinding {
      val walletDescriptor =
        watchingWalletDescriptorProvider.walletDescriptor(keyset)
          .logFailure { "Error creating watching wallet descriptor for keyset." }
          .bind()

      watchingWalletProvider
        .getWallet(walletDescriptor)
        .logFailure { "Error creating watching wallet for keyset." }
        .bind()
    }
}
