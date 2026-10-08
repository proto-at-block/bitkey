package build.wallet.bitcoin.wallet

import com.github.michaelbull.result.Result

internal class AppKeyWalletImpl(
  private val spendingWallet: SpendingWallet,
  private val fullScanOperation: suspend () -> Result<Unit, Error>,
) : AppKeyWallet,
  SpendingWallet by spendingWallet {
  override suspend fun fullScan(): Result<Unit, Error> = fullScanOperation()
}
