package bitkey.securitycenter

import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.EndorsedBeneficiaryFake
import build.wallet.compose.collections.emptyImmutableList
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class InheritanceActionTest : FunSpec({
  test("Test InheritanceActionTest with Recovery Contacts") {
    val relationships = Relationships(
      invitations = emptyList(),
      endorsedTrustedContacts = listOf(EndorsedBeneficiaryFake),
      unendorsedTrustedContacts = emptyImmutableList(),
      protectedCustomers = emptyImmutableList()
    )
    val healthyAction = InheritanceAction(relationships)
    healthyAction.getRecommendations().shouldBeEmpty()
    healthyAction.category() shouldBe SecurityActionCategory.RECOVERY
  }

  test("Test InheritanceAction without Recovery Contacts") {
    val relationships = Relationships(
      invitations = emptyList(),
      endorsedTrustedContacts = emptyList(),
      unendorsedTrustedContacts = emptyList(),
      protectedCustomers = emptyImmutableList()
    )
    val tcMissingAction = InheritanceAction(relationships)
    tcMissingAction.getRecommendations() shouldBe listOf(SecurityActionRecommendation.ADD_BENEFICIARY)
    tcMissingAction.category() shouldBe SecurityActionCategory.RECOVERY
  }
})
