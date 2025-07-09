package bitkey.securitycenter

import bitkey.relationships.Relationships
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.bitkey.relationships.EndorsedBeneficiaryFake
import build.wallet.compose.collections.emptyImmutableList
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class InheritanceActionTest : FunSpec({
  test("Test InheritanceActionTest with trusted contacts") {
    val relationships = Relationships(
      invitations = emptyList(),
      endorsedTrustedContacts = listOf(EndorsedBeneficiaryFake),
      unendorsedTrustedContacts = emptyImmutableList(),
      protectedCustomers = emptyImmutableList()
    )
    val healthyAction = InheritanceAction(
      relationships,
      FunctionalityFeatureStates.FeatureState.Available
    )
    healthyAction.getRecommendations().shouldBeEmpty()
    healthyAction.category() shouldBe SecurityActionCategory.RECOVERY
  }

  test("Test InheritanceAction without trusted contacts") {
    val relationships = Relationships(
      invitations = emptyList(),
      endorsedTrustedContacts = emptyList(),
      unendorsedTrustedContacts = emptyList(),
      protectedCustomers = emptyImmutableList()
    )
    val tcMissingAction = InheritanceAction(
      relationships,
      FunctionalityFeatureStates.FeatureState.Available
    )
    tcMissingAction.getRecommendations() shouldBe listOf(SecurityActionRecommendation.ADD_BENEFICIARY)
    tcMissingAction.category() shouldBe SecurityActionCategory.RECOVERY
  }

  test("Test InheritanceAction with disabled feature") {
    val relationships = Relationships(
      invitations = emptyList(),
      endorsedTrustedContacts = listOf(EndorsedBeneficiaryFake),
      unendorsedTrustedContacts = emptyImmutableList(),
      protectedCustomers = emptyImmutableList()
    )

    val action = InheritanceAction(
      relationships,
      FunctionalityFeatureStates.FeatureState.Unavailable
    )
    action.getRecommendations().shouldBeEmpty()
    action.category() shouldBe SecurityActionCategory.RECOVERY
    action.state() shouldBe SecurityActionState.Disabled
  }
})
