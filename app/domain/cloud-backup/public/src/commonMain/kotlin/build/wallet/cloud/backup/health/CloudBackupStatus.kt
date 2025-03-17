package build.wallet.cloud.backup.health

data class CloudBackupStatus(
  val mobileKeyBackupStatus: MobileKeyBackupStatus,
  val eakBackupStatus: EakBackupStatus,
)

fun CloudBackupStatus.isHealthy(): Boolean {
  return mobileKeyBackupStatus.isHealthy() && eakBackupStatus.isHealthy()
}
