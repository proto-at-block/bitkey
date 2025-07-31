package build.wallet.cloud.backup.health

import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.store.CloudStoreAccount

class FullAccountCloudBackupRepairerFake : FullAccountCloudBackupRepairer {
  data class AttemptRepairCall(
    val account: FullAccount,
    val cloudStoreAccount: CloudStoreAccount,
    val cloudBackupStatus: CloudBackupStatus,
  )

  val attemptRepairCalls = mutableListOf<AttemptRepairCall>()
  var onRepairAttempt: (suspend () -> Unit)? = null

  override suspend fun attemptRepair(
    account: FullAccount,
    cloudStoreAccount: CloudStoreAccount,
    cloudBackupStatus: CloudBackupStatus,
  ) {
    attemptRepairCalls.add(
      AttemptRepairCall(account, cloudStoreAccount, cloudBackupStatus)
    )
    onRepairAttempt?.invoke()
  }

  fun reset() {
    attemptRepairCalls.clear()
    onRepairAttempt = null
  }
}
