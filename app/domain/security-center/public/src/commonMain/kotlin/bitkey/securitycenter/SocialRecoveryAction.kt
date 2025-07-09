package bitkey.securitycenter

import bitkey.relationships.Relationships
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.bitkey.relationships.socialRecoveryInvitations
import build.wallet.bitkey.relationships.socialRecoveryTrustedContacts
import build.wallet.bitkey.relationships.socialRecoveryUnendorsedTrustedContacts

class SocialRecoveryAction(
  private val relationships: Relationships?,
  private val featureState: FunctionalityFeatureStates.FeatureState,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    if (relationships?.endorsedTrustedContacts?.socialRecoveryTrustedContacts()?.isNotEmpty() == true ||
      relationships?.unendorsedTrustedContacts?.socialRecoveryUnendorsedTrustedContacts()?.isNotEmpty() == true ||
      relationships?.invitations?.socialRecoveryInvitations()?.isNotEmpty() == true
    ) {
      return emptyList()
    }
    return listOf(SecurityActionRecommendation.ADD_TRUSTED_CONTACTS)
  }

  override fun category(): SecurityActionCategory = SecurityActionCategory.RECOVERY

  override fun type(): SecurityActionType = SecurityActionType.SOCIAL_RECOVERY

  override fun state(): SecurityActionState {
    return if (featureState != FunctionalityFeatureStates.FeatureState.Available) {
      SecurityActionState.Disabled
    } else if (relationships?.endorsedTrustedContacts.isNullOrEmpty()) {
      SecurityActionState.HasRecommendationActions
    } else {
      SecurityActionState.Secure
    }
  }

  override fun requiresAction(): Boolean {
    return relationships?.endorsedTrustedContacts?.socialRecoveryTrustedContacts()?.isEmpty() == true
  }
}
