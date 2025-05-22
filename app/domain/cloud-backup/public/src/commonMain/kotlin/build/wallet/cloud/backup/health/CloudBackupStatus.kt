package build.wallet.cloud.backup.health

data class CloudBackupStatus(
  val mobileKeyBackupStatus: MobileKeyBackupStatus,
  val eekBackupStatus: EekBackupStatus,
)

fun CloudBackupStatus.isHealthy(): Boolean {
  return mobileKeyBackupStatus.isHealthy() && eekBackupStatus.isHealthy()
}
