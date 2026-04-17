package build.wallet.cloud.backup.migration

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CloudKitBackupMigrationServiceFake : CloudKitBackupMigrationService {
  var migrateIfNeededResult: Result<Unit, Throwable> = Ok(Unit)
  var migrateIfNeededCallCount = 0
    private set

  override suspend fun migrateIfNeeded(): Result<Unit, Throwable> {
    migrateIfNeededCallCount++
    return migrateIfNeededResult
  }

  fun reset() {
    migrateIfNeededResult = Ok(Unit)
    migrateIfNeededCallCount = 0
  }
}
