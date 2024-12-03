package build.wallet.ui.app.inheritance

import androidx.compose.ui.Modifier
import build.wallet.bitkey.relationships.*
import build.wallet.compose.collections.immutableListOf
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.inheritance.BenefactorListModel
import build.wallet.statemachine.inheritance.BeneficiaryListModel
import build.wallet.statemachine.inheritance.ManagingInheritanceBodyModel
import build.wallet.statemachine.inheritance.ManagingInheritanceTab
import build.wallet.ui.model.StandardClick
import io.kotest.core.spec.style.FunSpec

class InheritanceManagementSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  val benefactors = BenefactorListModel(
    benefactors = listOf(ProtectedCustomerFake),
    onStartClaimClick = {}
  )
  val beneficiaries = BeneficiaryListModel(
    beneficiaries = immutableListOf(EndorsedBeneficiaryFake),
    inheritanceClaims = emptyList()
  )

  val beneficiariesPending = BeneficiaryListModel(
    beneficiaries = immutableListOf(InvitationFake),
    inheritanceClaims = emptyList()
  )

  test("inheritance management - inheritance tab - empty") {
    paparazzi.snapshot {
      ManagingInheritanceBodyModel(
        onBack = {},
        onInviteClick = StandardClick {},
        onTabRowClick = {},
        onAcceptInvitation = {},
        selectedTab = ManagingInheritanceTab.Inheritance,
        hasPendingBeneficiaries = false,
        beneficiaries = BeneficiaryListModel(
          beneficiaries = immutableListOf(),
          inheritanceClaims = emptyList()
        ),
        benefactors = BenefactorListModel(
          benefactors = emptyList(),
          onStartClaimClick = {}
        )
      ).render(modifier = Modifier)
    }
  }

  test("inheritance management - inheritance tab - accepted") {
    paparazzi.snapshot {
      ManagingInheritanceBodyModel(
        onBack = {},
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
        onInviteClick = StandardClick {},
        onTabRowClick = {},
        onAcceptInvitation = {},
        selectedTab = ManagingInheritanceTab.Beneficiaries,
        hasPendingBeneficiaries = false,
        beneficiaries = BeneficiaryListModel(
          beneficiaries = immutableListOf(),
          inheritanceClaims = emptyList()
        ),
        benefactors = BenefactorListModel(
          benefactors = emptyList(),
          onStartClaimClick = {}
        )
      ).render(modifier = Modifier)
    }
  }

  test("inheritance management - beneficiaries tab - with invite") {
    paparazzi.snapshot {
      ManagingInheritanceBodyModel(
        onBack = {},
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
