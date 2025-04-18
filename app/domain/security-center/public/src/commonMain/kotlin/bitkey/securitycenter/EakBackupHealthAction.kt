package bitkey.securitycenter

import build.wallet.cloud.backup.health.EakBackupStatus
import build.wallet.cloud.backup.health.isHealthy

data class EakBackupHealthAction(
  private val cloudBackupStatus: EakBackupStatus,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    return listOfNotNull(
      SecurityActionRecommendation.BACKUP_EAK.takeIf { !cloudBackupStatus.isHealthy() }
    )
  }

  override fun category(): SecurityActionCategory {
    return SecurityActionCategory.RECOVERY
  }

  override fun type(): SecurityActionType = SecurityActionType.EAK_BACKUP
}
