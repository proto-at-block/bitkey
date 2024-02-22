package build.wallet.bitcoin.transactions

import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding

class BitcoinTransactionAppSignerImpl(
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
) : BitcoinTransactionAppSigner {
  override suspend fun sign(
    keyset: SpendingKeyset,
    psbt: Psbt,
  ): Result<Psbt, Throwable> =
    binding {
      appSpendingWalletProvider
        .getSpendingWallet(keyset).bind()
        .signPsbt(psbt).bind()
    }.logFailure { "Failed to sign a PSBT using app key" }
}
