package bitkey.securitycenter

import build.wallet.cloud.backup.health.AppKeyBackupStatus
import build.wallet.cloud.backup.health.isHealthy

data class AppKeyBackupHealthAction(
  private val cloudBackupStatus: AppKeyBackupStatus,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    return listOfNotNull(
      SecurityActionRecommendation.BACKUP_MOBILE_KEY.takeIf { !cloudBackupStatus.isHealthy() }
    )
  }

  override fun category(): SecurityActionCategory {
    return SecurityActionCategory.RECOVERY
  }

  override fun type(): SecurityActionType = SecurityActionType.APP_KEY_BACKUP
}
