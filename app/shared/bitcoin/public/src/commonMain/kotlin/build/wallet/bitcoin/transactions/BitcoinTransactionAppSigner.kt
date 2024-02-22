package build.wallet.bitcoin.transactions

import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Result

interface BitcoinTransactionAppSigner {
  /**
   * Signs a Bitcoin transaction using mobile app's private key. Returns a copy of a transaction
   * with updated psbt value.
   */
  suspend fun sign(
    keyset: SpendingKeyset,
    psbt: Psbt,
  ): Result<Psbt, Throwable>
}
