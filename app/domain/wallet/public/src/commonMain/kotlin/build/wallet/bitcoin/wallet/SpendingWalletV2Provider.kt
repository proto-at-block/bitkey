package build.wallet.bitcoin.wallet

import com.github.michaelbull.result.Result

interface SpendingWalletV2Provider {
  /**
   * Provides a [SpendingWallet] & [WatchingWallet] using BDK v2 for a [WalletDescriptor].
   */
  fun getWallet(walletDescriptor: WalletDescriptor): Result<SpendingWallet, Throwable>
}
