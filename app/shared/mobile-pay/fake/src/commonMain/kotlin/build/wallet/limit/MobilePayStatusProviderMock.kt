package build.wallet.limit

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.keybox.Keybox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

class MobilePayStatusProviderMock(
  turbine: (String) -> Turbine<Any>,
) : MobilePayStatusProvider {
  var status = MutableStateFlow<MobilePayStatus?>(null)
  var refreshStatusCalls = turbine("force Mobile Pay status refresh")

  override suspend fun refreshStatus() {
    refreshStatusCalls += Unit
  }

  override fun status(
    keybox: Keybox,
    wallet: SpendingWallet,
  ): Flow<MobilePayStatus> = status.filterNotNull()

  fun reset() {
    status.value = null
  }
}
