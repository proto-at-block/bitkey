package bitkey.securitycenter

import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.socialRecoveryInvitations
import build.wallet.bitkey.relationships.socialRecoveryTrustedContacts
import build.wallet.bitkey.relationships.socialRecoveryUnendorsedTrustedContacts

class SocialRecoveryAction(
  private val relationships: Relationships?,
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

  override fun requiresAction(): Boolean {
    return relationships?.endorsedTrustedContacts?.socialRecoveryTrustedContacts()?.isEmpty() == true
  }
}
