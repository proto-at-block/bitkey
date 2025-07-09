package bitkey.securitycenter

import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.cloud.backup.health.AppKeyBackupStatus
import build.wallet.cloud.backup.health.isHealthy

data class AppKeyBackupHealthAction(
  private val cloudBackupStatus: AppKeyBackupStatus,
  private val featureState: FunctionalityFeatureStates.FeatureState,
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

  override fun state(): SecurityActionState {
    return if (featureState != FunctionalityFeatureStates.FeatureState.Available) {
      SecurityActionState.Disabled
    } else if (cloudBackupStatus.isHealthy()) {
      SecurityActionState.Secure
    } else {
      SecurityActionState.HasCriticalActions
    }
  }
}
