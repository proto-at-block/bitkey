package build.wallet.bitcoin.wallet

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AppKeyWalletFake(
  private val spendingWallet: SpendingWallet = SpendingWalletFake(),
) : AppKeyWallet,
  SpendingWallet by spendingWallet {
  var fullScanResult: Result<Unit, Error> = Ok(Unit)
  var fullScanCalls = 0

  override suspend fun fullScan(): Result<Unit, Error> {
    fullScanCalls += 1
    return fullScanResult
  }

  fun reset() {
    fullScanResult = Ok(Unit)
    fullScanCalls = 0
  }
}
