package bitkey.securitycenter

import bitkey.relationships.Relationships
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.bitkey.relationships.TrustedContactRole

class InheritanceAction(
  private val relationships: Relationships?,
  private val featureState: FunctionalityFeatureStates.FeatureState,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    val endorsedBeneficiaries = relationships?.endorsedTrustedContacts?.filter {
      it.roles.contains(TrustedContactRole.Beneficiary)
    }
    if (endorsedBeneficiaries?.isNotEmpty() == true) {
      return emptyList()
    }
    return listOf(SecurityActionRecommendation.ADD_BENEFICIARY)
  }

  override fun category(): SecurityActionCategory {
    return SecurityActionCategory.RECOVERY
  }

  override fun type(): SecurityActionType = SecurityActionType.INHERITANCE

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
