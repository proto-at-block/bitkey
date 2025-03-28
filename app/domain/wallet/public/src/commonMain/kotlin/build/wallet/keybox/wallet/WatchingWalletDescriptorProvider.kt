package build.wallet.keybox.wallet

import build.wallet.bitcoin.wallet.WatchingWalletDescriptor
import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Result

/*
 * Provider object for exporting a watching wallet descriptor
 */
interface WatchingWalletDescriptorProvider {
  /*
   * Provides a [WatchingWalletProvider] given a [SpendingKeyset]
   */
  suspend fun walletDescriptor(keyset: SpendingKeyset): Result<WatchingWalletDescriptor, Throwable>
}
