package build.wallet.ui.app.inheritance

import androidx.compose.ui.Modifier
import build.wallet.bitkey.relationships.*
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.inheritance.ContactClaimState
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.inheritance.BenefactorListModel
import build.wallet.statemachine.inheritance.BeneficiaryListModel
import build.wallet.statemachine.inheritance.ManagingInheritanceBodyModel
import build.wallet.statemachine.inheritance.ManagingInheritanceTab
import build.wallet.ui.model.StandardClick
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Instant

class InheritanceManagementSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  val benefactors = BenefactorListModel(
    benefactors = immutableListOf(
      ContactClaimState.Benefactor(
        timestamp = Instant.DISTANT_PAST,
        relationship = ProtectedCustomerFake,
        claims = immutableListOf()
      )
    ),
    onManageClick = {}
  )
  val beneficiaries = BeneficiaryListModel(
    beneficiaries = immutableListOf(
      ContactClaimState.Beneficiary(
        timestamp = Instant.DISTANT_PAST,
        relationship = EndorsedBeneficiaryFake,
        claims = immutableListOf(),
        isInvite = false
      )
    ),
    onManageClick = {}
  )

  val beneficiariesPending = BeneficiaryListModel(
    beneficiaries = immutableListOf(
      ContactClaimState.Beneficiary(
        timestamp = Instant.DISTANT_PAST,
        relationship = InvitationFake,
        claims = immutableListOf(),
        isInvite = true
      )
    ),
    onManageClick = {}
  )

  test("inheritance management - inheritance tab - empty") {
    paparazzi.snapshot {
      ManagingInheritanceBodyModel(
        onBack = {},
        onLearnMore = {},
        onInviteClick = StandardClick {},
        onTabRowClick = {},
        onAcceptInvitation = {},
        selectedTab = ManagingInheritanceTab.Inheritance,
        hasPendingBeneficiaries = false,
        beneficiaries = BeneficiaryListModel(
          beneficiaries = immutableListOf(),
          onManageClick = {}
        ),
        benefactors = BenefactorListModel(
          benefactors = emptyImmutableList(),
          onManageClick = {}
        )
      ).render(modifier = Modifier)
    }
  }

  test("inheritance management - inheritance tab - accepted") {
    paparazzi.snapshot {
      ManagingInheritanceBodyModel(
        onBack = {},
        onLearnMore = {},
        onInviteClick = StandardClick {},
        onTabRowClick = {},
        onAcceptInvitation = {},
        selectedTab = ManagingInheritanceTab.Inheritance,
        hasPendingBeneficiaries = true,
        benefactors = benefactors,
        beneficiaries = beneficiaries
      ).render(modifier = Modifier)
    }
  }

  test("inheritance management - beneficiaries tab - empty") {
    paparazzi.snapshot {
      ManagingInheritanceBodyModel(
        onBack = {},
        onLearnMore = {},
        onInviteClick = StandardClick {},
        onTabRowClick = {},
        onAcceptInvitation = {},
        selectedTab = ManagingInheritanceTab.Beneficiaries,
        hasPendingBeneficiaries = false,
        beneficiaries = BeneficiaryListModel(
          beneficiaries = immutableListOf(),
          onManageClick = {}
        ),
        benefactors = BenefactorListModel(
          benefactors = emptyImmutableList(),
          onManageClick = {}
        )
      ).render(modifier = Modifier)
    }
  }

  test("inheritance management - beneficiaries tab - with invite") {
    paparazzi.snapshot {
      ManagingInheritanceBodyModel(
        onBack = {},
        onLearnMore = {},
        onInviteClick = StandardClick {},
        onTabRowClick = {},
        onAcceptInvitation = {},
        selectedTab = ManagingInheritanceTab.Beneficiaries,
        hasPendingBeneficiaries = true,
        benefactors = benefactors,
        beneficiaries = beneficiariesPending
      ).render(modifier = Modifier)
    }
  }

  test("inheritance management - beneficiaries tab - accepted") {
    paparazzi.snapshot {
      ManagingInheritanceBodyModel(
        onBack = {},
        onLearnMore = {},
        onInviteClick = StandardClick {},
        onTabRowClick = {},
        onAcceptInvitation = {},
        selectedTab = ManagingInheritanceTab.Beneficiaries,
        hasPendingBeneficiaries = false,
        benefactors = benefactors,
        beneficiaries = beneficiaries
      ).render(modifier = Modifier)
    }
  }
})
