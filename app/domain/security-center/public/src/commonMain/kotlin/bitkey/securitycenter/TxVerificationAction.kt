package bitkey.securitycenter

import bitkey.verification.VerificationThreshold
import build.wallet.availability.FunctionalityFeatureStates

class TxVerificationAction(
  private val threshold: VerificationThreshold,
  private val featureState: FunctionalityFeatureStates.FeatureState,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    return if (threshold == VerificationThreshold.Disabled) {
      listOf(SecurityActionRecommendation.ENABLE_TRANSACTION_VERIFICATION)
    } else {
      emptyList()
    }
  }

  override fun category(): SecurityActionCategory {
    return SecurityActionCategory.SECURITY
  }

  override fun type(): SecurityActionType {
    return SecurityActionType.TRANSACTION_VERIFICATION
  }

  override fun state(): SecurityActionState {
    return if (featureState != FunctionalityFeatureStates.FeatureState.Available) {
      SecurityActionState.Disabled
    } else if (getRecommendations().isNotEmpty()) {
      SecurityActionState.HasRecommendationActions
    } else {
      SecurityActionState.Secure
    }
  }
}
