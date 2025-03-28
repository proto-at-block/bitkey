package build.wallet.keybox.wallet

import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.keybox.SoftwareKeybox
import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Result

/**
 * Provides [SpendingWallet] for app.
 */
interface AppSpendingWalletProvider {
  /**
   * Creates [SpendingWallet] instance for the given [keyset].
   *
   * Requires app private key for the keyset to be present in the app keystore, otherwise an error
   * is returned.
   *
   * Warn: If you need to a [SpendingWallet] for an active account, do not use [getSpendingWallet].
   * Instead, use [BitcoinWalletService.spendingWallet]. The service is responsible for creating
   * and caching a [SpendingWallet] for the active account.
   */
  suspend fun getSpendingWallet(keyset: SpendingKeyset): Result<SpendingWallet, Throwable>

  suspend fun getSpendingWallet(keybox: SoftwareKeybox): Result<SpendingWallet, Throwable>
}
