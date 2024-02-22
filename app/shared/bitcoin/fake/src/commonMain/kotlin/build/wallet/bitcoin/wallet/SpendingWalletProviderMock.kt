package build.wallet.bitcoin.wallet

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class SpendingWalletProviderMock(
  val wallet: SpendingWallet,
) : SpendingWalletProvider {
  override suspend fun getWallet(
    walletDescriptor: SpendingWalletDescriptor,
  ): Result<SpendingWallet, Throwable> = Ok(wallet)
}
