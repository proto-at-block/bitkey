package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bitcoin.wallet.WalletDescriptor
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class BdkWalletProviderMock(
  private val wallet: BdkWallet,
) : BdkWalletProvider {
  override suspend fun getBdkWallet(
    walletDescriptor: WalletDescriptor,
  ): Result<BdkWallet, BdkError> = Ok(wallet)
}
