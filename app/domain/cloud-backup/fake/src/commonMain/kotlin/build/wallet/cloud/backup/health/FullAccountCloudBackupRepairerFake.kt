package build.wallet.cloud.backup.health

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.store.CloudStoreAccount

class FullAccountCloudBackupRepairerFake : FullAccountCloudBackupRepairer {
  data class AttemptRepairCall(
    val accountId: FullAccountId,
    val keybox: Keybox,
    val cloudStoreAccount: CloudStoreAccount,
    val cloudBackupStatus: CloudBackupStatus,
  )

  val attemptRepairCalls = mutableListOf<AttemptRepairCall>()
  var onRepairAttempt: (suspend () -> Unit)? = null

  override suspend fun attemptRepair(
    accountId: FullAccountId,
    keybox: Keybox,
    cloudStoreAccount: CloudStoreAccount,
    cloudBackupStatus: CloudBackupStatus,
  ) {
    attemptRepairCalls.add(
      AttemptRepairCall(accountId, keybox, cloudStoreAccount, cloudBackupStatus)
    )
    onRepairAttempt?.invoke()
  }

  fun reset() {
    attemptRepairCalls.clear()
    onRepairAttempt = null
  }
}
