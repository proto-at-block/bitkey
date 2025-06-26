package build.wallet.cloud.backup.health

data class CloudBackupStatus(
  val appKeyBackupStatus: AppKeyBackupStatus,
  val eekBackupStatus: EekBackupStatus,
)

fun CloudBackupStatus.isHealthy(): Boolean {
  return appKeyBackupStatus.isHealthy() && eekBackupStatus.isHealthy()
}
