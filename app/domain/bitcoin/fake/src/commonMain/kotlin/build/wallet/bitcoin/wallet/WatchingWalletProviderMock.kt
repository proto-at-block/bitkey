package build.wallet.bitcoin.wallet

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class WatchingWalletProviderMock(
  var watchingWallet: WatchingWallet,
) : WatchingWalletProvider {
  override suspend fun getWallet(
    walletDescriptor: WatchingWalletDescriptor,
  ): Result<WatchingWallet, Throwable> {
    return Ok(watchingWallet)
  }
}
