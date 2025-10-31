package build.wallet.integration.statemachine.inheritance.robots

import build.wallet.statemachine.core.test
import build.wallet.statemachine.inheritance.InheritanceManagementUiProps
import build.wallet.statemachine.inheritance.ManageInheritanceContactBodyModel
import build.wallet.statemachine.inheritance.ManagingInheritanceBodyModel
import build.wallet.statemachine.inheritance.ManagingInheritanceTab
import build.wallet.statemachine.inheritance.claims.start.ClaimStartedBodyModel
import build.wallet.statemachine.inheritance.claims.start.StartClaimConfirmationBodyModel
import build.wallet.statemachine.inheritance.claims.start.StartClaimConfirmationPromptBodyModel
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelsSettingsFormBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilSheet
import build.wallet.statemachine.ui.robots.advanceUntilScreenWithBody
import build.wallet.testing.shouldBeOk
import build.wallet.ui.model.list.ListItemAccessory
import com.github.michaelbull.result.get
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.seconds

/**
 * Starts an inheritance claim for the beneficiary via the management UI.
 */
suspend fun InheritanceTestApps.advanceThroughClaimStart() {
  beneficiary.app.inheritanceManagementUiStateMachine.test(
    turbineTimeout = 60.seconds,
    props = InheritanceManagementUiProps(
      account = beneficiary.account(),
      selectedTab = ManagingInheritanceTab.Beneficiaries,
      onBack = { error("No exit calls expected") },
      onGoToUtxoConsolidation = { }
    )
  ) {
    awaitUntilBody<ManagingInheritanceBodyModel>(
      matching = { model ->
        model.benefactors.items.any { it.title == benefactor.name }
      }
    ) {
      benefactors
        .items
        .single { it.title == benefactor.name }
        .trailingAccessory
        .shouldNotBeNull()
        .shouldBeInstanceOf<ListItemAccessory.ButtonAccessory>()
        .model
        .onClick()
    }

    awaitUntilSheet<ManageInheritanceContactBodyModel>(
      matching = {
        it.claimControls is ManageInheritanceContactBodyModel.ClaimControls.Start
      }
    ) {
      claimControls.shouldBeInstanceOf<ManageInheritanceContactBodyModel.ClaimControls.Start>()
        .onClick()
    }
    advanceUntilScreenWithBody<RecoveryChannelsSettingsFormBodyModel> {
      continueOnClick.shouldNotBeNull().invoke()
    }
    awaitUntilBody<StartClaimConfirmationBodyModel> {
      onContinue()
    }
    awaitUntilSheet<StartClaimConfirmationPromptBodyModel> {
      onConfirm()
    }
    awaitUntilBody<ClaimStartedBodyModel> {
      onClose()
    }
    awaitUntilBody<ManagingInheritanceBodyModel>()
    cancelAndIgnoreRemainingEvents()
  }
}

suspend fun InheritanceTestApps.skipDelayPeriod() {
  val claimId = beneficiary.app.claimsRepository.fetchClaims()
    .get()
    ?.beneficiaryClaims
    ?.singleOrNull()
    .shouldNotBeNull()
    .claimId
  beneficiary.app.shortenClaimF8eClient.shortenClaim(beneficiary.account(), claimId, 0.seconds)
    .shouldBeOk()
}
