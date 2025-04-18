package bitkey.securitycenter

import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.TrustedContactRole

class InheritanceAction(
  private val relationships: Relationships?,
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
}
