package build.wallet.bitcoin.wallet

import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AppKeyWalletProviderFake(
  var walletResult: Result<AppKeyWallet, Throwable> = Ok(AppKeyWalletFake()),
) : AppKeyWalletProvider {
  val getAppKeyWalletCalls = mutableListOf<SpendingKeyset>()

  override suspend fun getAppKeyWallet(keyset: SpendingKeyset): Result<AppKeyWallet, Throwable> {
    getAppKeyWalletCalls.add(keyset)
    return walletResult
  }

  fun reset() {
    walletResult = Ok(AppKeyWalletFake())
    getAppKeyWalletCalls.clear()
  }
}
