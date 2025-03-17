package build.wallet.limit

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.account.FullAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

class MobilePayStatusRepositoryMock(
  turbine: (String) -> Turbine<Any>,
) : MobilePayStatusRepository {
  var status = MutableStateFlow<MobilePayStatus?>(null)
  var refreshStatusCalls = turbine("force Mobile Pay status refresh")

  override suspend fun refreshStatus() {
    refreshStatusCalls += Unit
  }

  override fun status(account: FullAccount): Flow<MobilePayStatus> = status.filterNotNull()

  fun reset() {
    status.value = null
  }
}
