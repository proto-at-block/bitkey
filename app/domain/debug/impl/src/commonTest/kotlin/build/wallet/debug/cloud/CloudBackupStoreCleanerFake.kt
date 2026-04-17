package build.wallet.debug.cloud

import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CloudBackupStoreCleanerFake : CloudBackupStoreCleaner {
  data class DeleteCall(
    val type: CloudBackupStoreType,
    val cloudStoreAccount: CloudStoreAccount,
  )

  val deleteCalls = mutableListOf<DeleteCall>()

  override suspend fun deleteBackupsIn(
    type: CloudBackupStoreType,
    cloudStoreAccount: CloudStoreAccount,
  ): Result<Unit, Throwable> {
    deleteCalls += DeleteCall(type = type, cloudStoreAccount = cloudStoreAccount)
    return Ok(Unit)
  }

  fun reset() {
    deleteCalls.clear()
  }
}
