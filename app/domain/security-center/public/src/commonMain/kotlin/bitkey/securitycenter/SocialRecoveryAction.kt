package bitkey.securitycenter

import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.socialRecoveryTrustedContacts

class SocialRecoveryAction(
  private val relationships: Relationships?,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    if (relationships?.endorsedTrustedContacts?.socialRecoveryTrustedContacts()?.isNotEmpty() == true) {
      return emptyList()
    }
    return listOf(SecurityActionRecommendation.ADD_TRUSTED_CONTACTS)
  }

  override fun category(): SecurityActionCategory = SecurityActionCategory.RECOVERY

  override fun type(): SecurityActionType = SecurityActionType.SOCIAL_RECOVERY
}
