package build.wallet.integration.statemachine.inheritance

import build.wallet.bitkey.inheritance.BenefactorClaim
import build.wallet.bitkey.inheritance.InheritanceClaimId
import build.wallet.integration.statemachine.inheritance.robots.advanceThroughClaimStart
import build.wallet.integration.statemachine.inheritance.robots.launchInheritanceApps
import build.wallet.statemachine.core.test
import build.wallet.statemachine.inheritance.DeclineClaimBodyModel
import build.wallet.statemachine.inheritance.InheritanceManagementUiProps
import build.wallet.statemachine.inheritance.ManageInheritanceContactBodyModel
import build.wallet.statemachine.inheritance.ManageInheritanceContactBodyModel.ClaimControls.Cancel
import build.wallet.statemachine.inheritance.ManagingInheritanceBodyModel
import build.wallet.statemachine.inheritance.ManagingInheritanceTab
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilSheet
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.seconds

class DeclineInheritanceClaimIntegrationTests : FunSpec({
  // Background context: https://linear.app/squareup/issue/W-15459
  test("Benefactor declines pending claim when relationship has older canceled claim") {
    val apps = launchInheritanceApps()
    apps.advanceThroughClaimStart()
    apps.benefactor.app.claimsRepository.syncServerClaims()

    val pendingClaim = apps.benefactor.app.inheritanceService.pendingBenefactorClaim(
      relationshipId = apps.relationshipId
    ).shouldNotBeNull()

    val olderCanceledClaim = BenefactorClaim.CanceledClaim(
      claimId = InheritanceClaimId("stale-canceled-claim-${pendingClaim.claimId.value}"),
      relationshipId = apps.relationshipId
    )

    apps.benefactor.app.claimsRepository.updateSingleClaim(olderCanceledClaim)
    // Re-adding the pending claim puts it behind the stale claim in current repository ordering.
    apps.benefactor.app.claimsRepository.updateSingleClaim(pendingClaim)

    apps.benefactor.app.inheritanceManagementUiStateMachine.test(
      turbineTimeout = 60.seconds,
      props = InheritanceManagementUiProps(
        account = apps.benefactor.account(),
        selectedTab = ManagingInheritanceTab.Beneficiaries,
        onBack = { error("No exit calls expected") },
        onGoToUtxoConsolidation = { error("No UTXO consolidation expected") }
      )
    ) {
      awaitUntilBody<ManagingInheritanceBodyModel>(
        matching = { model ->
          model.beneficiaries.items.any { it.title == apps.beneficiary.name } &&
            model.beneficiaries.items.single {
              it.title == apps.beneficiary.name
            }.secondaryText == "Claim pending"
        }
      ) {
        beneficiaries.items.single { it.title == apps.beneficiary.name }
          .trailingAccessory
          .shouldBeInstanceOf<ButtonAccessory>()
          .model
          .onClick()
      }
      awaitUntilSheet<ManageInheritanceContactBodyModel>(
        matching = { it.claimControls is Cancel }
      ) {
        claimControls.shouldBeInstanceOf<Cancel>()
          .onClick()
      }
      awaitUntilBody<DeclineClaimBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }
})
