package bitkey.securitycenter

import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceService
import kotlinx.coroutines.flow.first

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
}

interface InheritanceActionFactory {
  suspend fun create(): SecurityAction?
}

@BitkeyInject(AppScope::class)
class InheritanceActionFactoryImpl(
  private val inheritanceService: InheritanceService,
) : InheritanceActionFactory {
  override suspend fun create(): SecurityAction {
    return InheritanceAction(inheritanceService.inheritanceRelationships.first())
  }
}
