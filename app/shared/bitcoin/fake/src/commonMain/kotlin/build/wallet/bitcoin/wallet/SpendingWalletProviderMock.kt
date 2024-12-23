package build.wallet.bitcoin.wallet

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class SpendingWalletProviderMock(
  var walletResult: Result<SpendingWallet, Throwable> = Ok(SpendingWalletFake()),
) : SpendingWalletProvider {
  override suspend fun getWallet(
    walletDescriptor: SpendingWalletDescriptor,
  ): Result<SpendingWallet, Throwable> {
    return walletResult
  }
}
