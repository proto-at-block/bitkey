package build.wallet.bitcoin.wallet

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class WalletV2ProviderMock(
  var walletResult: Result<SpendingWallet, Throwable> = Ok(SpendingWalletFake()),
) : WalletV2Provider {
  val requestedDescriptors = MutableStateFlow<List<WalletDescriptor>>(emptyList())

  override fun getWallet(walletDescriptor: WalletDescriptor): Result<SpendingWallet, Throwable> {
    requestedDescriptors.update { it + walletDescriptor }
    return walletResult
  }

  fun reset() {
    requestedDescriptors.update { emptyList() }
  }
}
