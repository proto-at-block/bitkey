package bitkey.securitycenter

import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.cloud.backup.health.isHealthy

data class EekBackupHealthAction(
  private val cloudBackupStatus: EekBackupStatus,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    return listOfNotNull(
      SecurityActionRecommendation.BACKUP_EAK.takeIf { !cloudBackupStatus.isHealthy() }
    )
  }

  override fun category(): SecurityActionCategory {
    return SecurityActionCategory.RECOVERY
  }

  override fun type(): SecurityActionType = SecurityActionType.EEK_BACKUP
}
