package build.wallet.keybox.wallet

import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Result

interface KeysetWalletProvider {
  /**
   * Provides [WatchingWallet] keyset for given [SpendingKeyset]. We don't need to have private
   * key associated with the keyset to create a watching wallet.
   *
   * This API is mostly used to sync and watch inactive keysets.
   */
  suspend fun getWatchingWallet(keyset: SpendingKeyset): Result<WatchingWallet, Throwable>
}
