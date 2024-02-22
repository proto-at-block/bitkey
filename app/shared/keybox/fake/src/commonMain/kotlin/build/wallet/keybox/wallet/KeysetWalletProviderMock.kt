package build.wallet.keybox.wallet

import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class KeysetWalletProviderMock : KeysetWalletProvider {
  var watchingWallet: WatchingWallet = SpendingWalletFake()

  override suspend fun getWatchingWallet(
    keyset: SpendingKeyset,
  ): Result<WatchingWallet, Throwable> {
    return Ok(watchingWallet)
  }
}
