package build.wallet.cloud.backup.health

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CloudBackupHealthRepositoryMock(
  turbine: (String) -> Turbine<Any?>,
) : CloudBackupHealthRepository {
  val mobileKeyBackupStatus = MutableStateFlow<MobileKeyBackupStatus?>(null)

  override fun mobileKeyBackupStatus(): StateFlow<MobileKeyBackupStatus?> {
    return mobileKeyBackupStatus
  }

  val eekBackupStatus = MutableStateFlow<EekBackupStatus?>(null)

  override fun eekBackupStatus(): StateFlow<EekBackupStatus?> {
    return eekBackupStatus
  }

  val syncLoopCalls = turbine("syncLoop calls")

  override suspend fun syncLoop(account: FullAccount) {
    syncLoopCalls += Unit
  }

  val performSyncCalls = turbine("performSync calls")

  override suspend fun performSync(account: FullAccount): CloudBackupStatus {
    performSyncCalls += Unit
    return CloudBackupStatus(
      mobileKeyBackupStatus = MobileKeyBackupStatus.ProblemWithBackup.NoCloudAccess,
      eekBackupStatus = EekBackupStatus.ProblemWithBackup.NoCloudAccess
    )
  }

  val requestSyncCalls = turbine("requestSync calls")

  override fun requestSync(account: FullAccount) {
    requestSyncCalls += Unit
  }

  fun reset() {
    mobileKeyBackupStatus.value = null
    eekBackupStatus.value = null
  }
}
