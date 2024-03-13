package build.wallet.statemachine.data.keybox

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.account.FullAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock

class CloudBackupRefresherFake(turbine: (String) -> Turbine<Any>) : CloudBackupRefresher {
  val refreshCloudBackupsWhenNecessaryCalls = turbine("refresh when necessary calls")

  override val lastCheck = MutableStateFlow(Clock.System.now())

  override suspend fun refreshCloudBackupsWhenNecessary(
    scope: CoroutineScope,
    fullAccount: FullAccount,
  ) {
    refreshCloudBackupsWhenNecessaryCalls += fullAccount
  }
}
