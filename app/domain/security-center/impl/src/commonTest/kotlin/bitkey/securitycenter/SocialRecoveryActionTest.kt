package bitkey.securitycenter

import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.compose.collections.emptyImmutableList
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class SocialRecoveryActionTest : FunSpec({
  test("Test SocialRecoveryActionTest with Recovery Contacts") {
    val relationships = Relationships(
      invitations = emptyList(),
      endorsedTrustedContacts = listOf(EndorsedTrustedContactFake1),
      unendorsedTrustedContacts = emptyImmutableList(),
      protectedCustomers = emptyImmutableList()
    )
    val healthyAction = SocialRecoveryAction(relationships)
    healthyAction.getRecommendations().shouldBeEmpty()
    healthyAction.category() shouldBe SecurityActionCategory.RECOVERY
  }

  test("Test SocialRecoveryActionTest without Recovery Contacts") {
    val relationships = Relationships(
      invitations = emptyList(),
      endorsedTrustedContacts = emptyList(),
      unendorsedTrustedContacts = emptyList(),
      protectedCustomers = emptyImmutableList()
    )
    val tcMissingAction = SocialRecoveryAction(relationships)
    tcMissingAction.getRecommendations() shouldBe listOf(SecurityActionRecommendation.ADD_TRUSTED_CONTACTS)
    tcMissingAction.category() shouldBe SecurityActionCategory.RECOVERY
  }
})
