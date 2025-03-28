package bitkey.securitycenter

import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.socialRecoveryTrustedContacts
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.socrec.SocRecService

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
}

interface SocialRecoveryActionFactory {
  suspend fun create(): SecurityAction
}

@BitkeyInject(AppScope::class)
class SocialRecoveryActionFactoryImpl(
  private val socRecService: SocRecService,
) : SocialRecoveryActionFactory {
  override suspend fun create(): SecurityAction {
    val relationships = socRecService.socRecRelationships.value
    return SocialRecoveryAction(relationships)
  }
}
