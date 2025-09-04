package build.wallet.bitcoin.wallet

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class WatchingWalletProviderMock(
  var watchingWallet: WatchingWallet,
) : WatchingWalletProvider {
  val requestedDescriptors = MutableStateFlow<List<WatchingWalletDescriptor>>(emptyList())

  override suspend fun getWallet(
    walletDescriptor: WatchingWalletDescriptor,
  ): Result<WatchingWallet, Throwable> {
    requestedDescriptors.update { it + walletDescriptor }
    return Ok(watchingWallet)
  }

  fun reset() {
    requestedDescriptors.update { emptyList() }
  }
}
