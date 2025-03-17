package build.wallet.bitcoin.wallet

import com.github.michaelbull.result.Result

interface WatchingWalletProvider {
  /**
   * Provides a [WatchingWallet] for a [WatchingWalletDescriptor].
   */
  suspend fun getWallet(
    walletDescriptor: WatchingWalletDescriptor,
  ): Result<WatchingWallet, Throwable>
}
