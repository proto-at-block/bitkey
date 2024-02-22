package build.wallet.bitcoin.wallet

import com.github.michaelbull.result.Result

interface SpendingWalletProvider {
  /**
   * Provides a [SpendingWallet] for a [SpendingWalletDescriptor].
   */
  suspend fun getWallet(
    walletDescriptor: SpendingWalletDescriptor,
  ): Result<SpendingWallet, Throwable>
}
