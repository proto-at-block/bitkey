package build.wallet.cloud.backup.health

class CloudBackupHealthSyncWorkerFake : CloudBackupHealthSyncWorker {
  var executeWorkCallCount = 0
    private set

  override suspend fun executeWork() {
    executeWorkCallCount++
  }

  fun reset() {
    executeWorkCallCount = 0
  }
}
