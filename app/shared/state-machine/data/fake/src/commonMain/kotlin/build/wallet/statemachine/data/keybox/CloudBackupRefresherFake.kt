package build.wallet.statemachine.data.keybox

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.account.FullAccount
import kotlinx.coroutines.CoroutineScope

class CloudBackupRefresherFake(turbine: (String) -> Turbine<Any>) : CloudBackupRefresher {
  val refreshCloudBackupsWhenNecessaryCalls = turbine("refresh when necessary calls")

  override suspend fun refreshCloudBackupsWhenNecessary(
    scope: CoroutineScope,
    fullAccount: FullAccount,
  ) {
    refreshCloudBackupsWhenNecessaryCalls += fullAccount
  }
}
