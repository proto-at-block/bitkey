package build.wallet.cloud.backup.health

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackupHealthRepository
import kotlinx.datetime.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CloudBackupHealthRepositoryMock(
  turbine: (String) -> Turbine<Any?>,
) : CloudBackupHealthRepository {
  val appKeyBackupStatus = MutableStateFlow<AppKeyBackupStatus?>(null)

  override fun appKeyBackupStatus(): StateFlow<AppKeyBackupStatus?> {
    return appKeyBackupStatus
  }

  val eekBackupStatus = MutableStateFlow<EekBackupStatus?>(null)

  override fun eekBackupStatus(): StateFlow<EekBackupStatus?> {
    return eekBackupStatus
  }

  val performSyncCalls = turbine("performSync calls")

  var syncResult: CloudBackupStatus = CloudBackupStatus(
    appKeyBackupStatus = AppKeyBackupStatus.Healthy(lastUploaded = Instant.DISTANT_PAST),
    eekBackupStatus = EekBackupStatus.Healthy(lastUploaded = Instant.DISTANT_PAST)
  )

  override suspend fun performSync(
    accountId: FullAccountId,
    keybox: Keybox,
  ): CloudBackupStatus {
    performSyncCalls += keybox
    appKeyBackupStatus.value = syncResult.appKeyBackupStatus
    eekBackupStatus.value = syncResult.eekBackupStatus
    return syncResult
  }

  fun reset() {
    appKeyBackupStatus.value = null
    eekBackupStatus.value = null
    syncResult = CloudBackupStatus(
      appKeyBackupStatus = AppKeyBackupStatus.Healthy(lastUploaded = Instant.DISTANT_PAST),
      eekBackupStatus = EekBackupStatus.Healthy(lastUploaded = Instant.DISTANT_PAST)
    )
  }
}
