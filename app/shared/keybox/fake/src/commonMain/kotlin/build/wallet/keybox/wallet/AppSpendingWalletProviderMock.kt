package build.wallet.keybox.wallet

import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AppSpendingWalletProviderMock(
  var spendingWallet: SpendingWallet,
) : AppSpendingWalletProvider {
  override suspend fun getSpendingWallet(
    keyset: SpendingKeyset,
  ): Result<SpendingWallet, Throwable> {
    return Ok(spendingWallet)
  }
}
