package bitkey.securitycenter

import bitkey.relationships.Relationships
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.compose.collections.emptyImmutableList
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class SocialRecoveryActionTest : FunSpec({
  test("Test SocialRecoveryActionTest with trusted contacts") {
    val relationships = Relationships(
      invitations = emptyList(),
      endorsedTrustedContacts = listOf(EndorsedTrustedContactFake1),
      unendorsedTrustedContacts = emptyImmutableList(),
      protectedCustomers = emptyImmutableList()
    )
    val healthyAction = SocialRecoveryAction(relationships, Available)
    healthyAction.getRecommendations().shouldBeEmpty()
    healthyAction.category() shouldBe SecurityActionCategory.RECOVERY
  }

  test("Test SocialRecoveryActionTest without trusted contacts") {
    val relationships = Relationships(
      invitations = emptyList(),
      endorsedTrustedContacts = emptyList(),
      unendorsedTrustedContacts = emptyList(),
      protectedCustomers = emptyImmutableList()
    )
    val tcMissingAction = SocialRecoveryAction(relationships, Available)
    tcMissingAction.getRecommendations() shouldBe listOf(SecurityActionRecommendation.ADD_TRUSTED_CONTACTS)
    tcMissingAction.category() shouldBe SecurityActionCategory.RECOVERY
  }

  test("Test SocialRecoveryAction with disabled feature") {
    val relationships = Relationships(
      invitations = emptyList(),
      endorsedTrustedContacts = listOf(EndorsedTrustedContactFake1),
      unendorsedTrustedContacts = emptyImmutableList(),
      protectedCustomers = emptyImmutableList()
    )

    val action = SocialRecoveryAction(
      relationships,
      FunctionalityFeatureStates.FeatureState.Unavailable
    )
    action.getRecommendations().shouldBeEmpty()
    action.category() shouldBe SecurityActionCategory.RECOVERY
    action.state() shouldBe SecurityActionState.Disabled
  }
})
