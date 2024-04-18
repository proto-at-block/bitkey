package build.wallet.keybox.wallet

import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.FullAccount
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
   */
  suspend fun getSpendingWallet(keyset: SpendingKeyset): Result<SpendingWallet, Throwable>

  /**
   * Creates [SpendingWallet] using the active spending keyset of the given [account].
   *
   * Requires app private key for the keyset to be present in the app keystore, otherwise an error
   * is returned.
   */
  suspend fun getSpendingWallet(account: FullAccount): Result<SpendingWallet, Throwable> =
    getSpendingWallet(account.keybox.activeSpendingKeyset)
}
