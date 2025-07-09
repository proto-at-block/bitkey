package bitkey.securitycenter

import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.cloud.backup.health.isHealthy

data class EekBackupHealthAction(
  private val cloudBackupStatus: EekBackupStatus,
  private val featureState: FunctionalityFeatureStates.FeatureState,
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

  override fun state(): SecurityActionState {
    return if (featureState != FunctionalityFeatureStates.FeatureState.Available) {
      SecurityActionState.Disabled
    } else if (cloudBackupStatus.isHealthy()) {
      SecurityActionState.Secure
    } else {
      SecurityActionState.HasRecommendationActions
    }
  }
}
