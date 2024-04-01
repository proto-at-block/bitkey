package build.wallet.cloud.backup.health

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.LoadableValue
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CloudBackupHealthRepositoryMock(
  turbine: (String) -> Turbine<Any?>,
) : CloudBackupHealthRepository {
  val mobileKeyBackupStatus = MutableStateFlow<LoadableValue<MobileKeyBackupStatus>>(
    LoadableValue.InitialLoading
  )

  override fun mobileKeyBackupStatus(): StateFlow<LoadableValue<MobileKeyBackupStatus>> {
    return mobileKeyBackupStatus
  }

  val eakBackupStatus = MutableStateFlow(LoadableValue.InitialLoading)

  override fun eakBackupStatus(): StateFlow<LoadableValue<EakBackupStatus>> {
    return eakBackupStatus
  }

  val syncLoopCalls = turbine("syncLoop calls")

  override suspend fun syncLoop(
    scope: CoroutineScope,
    account: FullAccount,
  ) {
    syncLoopCalls += Unit
  }

  val performSyncCalls = turbine("performSync calls")

  override suspend fun performSync(account: FullAccount): CloudBackupStatus {
    performSyncCalls += Unit
    return CloudBackupStatus(
      mobileKeyBackupStatus = MobileKeyBackupStatus.ProblemWithBackup.NoCloudAccess,
      eakBackupStatus = EakBackupStatus.ProblemWithBackup.NoCloudAccess
    )
  }

  val requestSyncCalls = turbine("requestSync calls")

  override fun requestSync(account: FullAccount) {
    requestSyncCalls += Unit
  }

  fun reset() {
    mobileKeyBackupStatus.value = LoadableValue.InitialLoading
    eakBackupStatus.value = LoadableValue.InitialLoading
  }
}
