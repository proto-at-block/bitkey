package build.wallet.cloud.backup.migration

import build.wallet.worker.BackgroundStrategy
import build.wallet.worker.RunStrategy

class CloudBackupVersionMigrationWorkerFake : CloudBackupVersionMigrationWorker {
  override val runStrategy: Set<RunStrategy> = setOf(
    RunStrategy.Startup(backgroundStrategy = BackgroundStrategy.Skip)
  )

  var executeWorkCallCount = 0
    private set

  override suspend fun executeWork() {
    executeWorkCallCount++
  }

  fun reset() {
    executeWorkCallCount = 0
  }
}
