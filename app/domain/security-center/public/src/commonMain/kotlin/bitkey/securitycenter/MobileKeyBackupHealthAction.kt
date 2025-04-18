package bitkey.securitycenter

import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.cloud.backup.health.isHealthy

data class MobileKeyBackupHealthAction(
  private val cloudBackupStatus: MobileKeyBackupStatus,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    return listOfNotNull(
      SecurityActionRecommendation.BACKUP_MOBILE_KEY.takeIf { !cloudBackupStatus.isHealthy() }
    )
  }

  override fun category(): SecurityActionCategory {
    return SecurityActionCategory.RECOVERY
  }

  override fun type(): SecurityActionType = SecurityActionType.MOBILE_KEY_BACKUP
}
