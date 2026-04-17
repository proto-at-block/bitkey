package build.wallet.integration.statemachine.inheritance.robots

import app.cash.turbine.ReceiveTurbine
import bitkey.account.HardwareType
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.statemachine.automations.AutomaticUiTests
import build.wallet.statemachine.automations.AutomationUnavailable
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.input.NameInputBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.inheritance.InheritanceManagementUiProps
import build.wallet.statemachine.inheritance.ManagingInheritanceBodyModel
import build.wallet.statemachine.inheritance.ManagingInheritanceTab
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.HardwareConfirmationResultBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.recovery.socrec.add.SaveContactBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.trustedcontact.model.EnteringBenefactorNameBodyModel
import build.wallet.statemachine.trustedcontact.model.EnteringInviteCodeBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.robots.advanceUntilScreenWithBody
import build.wallet.statemachine.ui.robots.clickInheritance
import build.wallet.statemachine.ui.robots.clickSettings
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import io.kotest.assertions.failure
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Test Applications initialized with an inheritance relationship.
 */
data class InheritanceTestApps(
  /**
   * The relationship ID between the benefactor/beneficiary.
   */
  val relationshipId: RelationshipId,
  /**
   * Test data related to the benefactor.
   */
  val benefactor: Instance,
  /**
   * Test data related to the beneficiary.
   */
  val beneficiary: Instance,
) {
  data class Instance(
    /**
     * App test instance for this user.
     */
    val app: AppTester,
    /**
     * The name of this user used in contact management.
     */
    val name: String,
  ) {
    /**
     * Get the full account object for this user.
     */
    suspend fun account(): FullAccount = app.getActiveFullAccount()
  }
}

/**
 * Launch and onboard two apps, associated by an inheritance relationship.
 */
suspend fun TestScope.launchInheritanceApps(
  benefactorName: String = "alice",
  beneficiaryName: String = "bob",
  benefactorHardwareType: HardwareType = HardwareType.W1,
  beneficiaryHardwareType: HardwareType = HardwareType.W1,
): InheritanceTestApps {
  val benefactorApp = launchNewApp(executeWorkers = false)
  val beneficiaryApp = launchNewApp(cloudBackupStore = benefactorApp.cloudBackupStore)
  return setupInheritanceBetween(
    benefactorApp = benefactorApp,
    beneficiaryApp = beneficiaryApp,
    benefactorName = benefactorName,
    beneficiaryName = beneficiaryName,
    benefactorHardwareType = benefactorHardwareType,
    beneficiaryHardwareType = beneficiaryHardwareType
  )
}

suspend fun TestScope.setupInheritanceBetween(
  benefactorApp: AppTester,
  beneficiaryApp: AppTester,
  benefactorName: String = "alice",
  beneficiaryName: String = "bob",
  benefactorHardwareType: HardwareType = HardwareType.W1,
  beneficiaryHardwareType: HardwareType = HardwareType.W1,
): InheritanceTestApps {
  benefactorApp.onboardFullAccountWithFakeHardware(
    cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake,
    hardwareType = benefactorHardwareType
  )

  beneficiaryApp.onboardFullAccountWithFakeHardware(
    cloudStoreAccountForBackup = CloudStoreAccountFake.TrustedContactFake,
    hardwareType = beneficiaryHardwareType
  )
  benefactorApp.inheritanceManagementUiStateMachine.test(
    props = InheritanceManagementUiProps(
      account = benefactorApp.getActiveFullAccount(),
      selectedTab = ManagingInheritanceTab.Beneficiaries,
      onBack = { error("No exit calls expected") },
      onGoToUtxoConsolidation = { }
    )
  ) {
    awaitUntilBody<ManagingInheritanceBodyModel>()
      .onInviteClick()
    advanceUntilScreenWithBody<NameInputBodyModel> {
      onValueChange(beneficiaryName)
    }
    advanceUntilScreenWithBody<SaveContactBodyModel> {
      tosInfo.shouldNotBeNull().onTermsAgreeToggle(true)
    }
    awaitUntilBody<SaveContactBodyModel> {
      onSave()
    }
    advanceUntilSuccessBodyHandling()
    cancelAndIgnoreRemainingEvents()
  }

  val inviteCode = benefactorApp.getSharedInviteCode()

  lateinit var relationshipId: String
  beneficiaryApp.appUiStateMachine.test(
    props = Unit
  ) {
    advanceThroughFullAccountAcceptTCInviteScreens(
      inviteCode = inviteCode,
      protectedCustomerAlias = benefactorName
    )
    relationshipId = beneficiaryApp.awaitRelationships {
      !it.protectedCustomers.isEmpty()
    }
      .protectedCustomers
      .first()
      .relationshipId
    cancelAndIgnoreRemainingEvents()
  }

  benefactorApp.awaitTcIsVerifiedAndBackedUp(relationshipId)

  return InheritanceTestApps(
    relationshipId = RelationshipId(relationshipId),
    benefactor = InheritanceTestApps.Instance(
      name = benefactorName,
      app = benefactorApp
    ),
    beneficiary = InheritanceTestApps.Instance(
      name = beneficiaryName,
      app = beneficiaryApp
    )
  )
}

/**
 * Advances through the full account invite screens for a beneficiary receiving an
 * inheritance invite.
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceThroughFullAccountAcceptTCInviteScreens(
  inviteCode: String,
  protectedCustomerAlias: String,
) {
  awaitUntilBody<MoneyHomeBodyModel>()
    .clickSettings()

  awaitUntilBody<SettingsBodyModel> {
    clickInheritance()
  }

  awaitUntilBody<ManagingInheritanceBodyModel>(
    matching = { it.selectedTab == ManagingInheritanceTab.Beneficiaries }
  ) {
    onTabRowClick(ManagingInheritanceTab.Inheritance)
  }

  awaitUntilBody<ManagingInheritanceBodyModel>(
    matching = { it.selectedTab == ManagingInheritanceTab.Inheritance }
  ) {
    onAcceptInvitation()
  }

  awaitUntilBody<EnteringInviteCodeBodyModel> {
    onValueChange(inviteCode)
  }

  advanceUntilScreenWithBody<EnteringBenefactorNameBodyModel> {
    onValueChange(protectedCustomerAlias)
  }

  advanceUntilSuccessBodyHandling()
}

@Suppress("ThrowsCount")
private suspend fun ReceiveTurbine<ScreenModel>.advanceUntilSuccessBodyHandling() {
  while (currentCoroutineContext().isActive) {
    val screen = awaitItem()
    // W3: PromptSelectionFormBodyModel appears in the bottom sheet, not as the main body
    val bottomBody = screen.bottomSheetModel?.body
    if (bottomBody is PromptSelectionFormBodyModel) {
      bottomBody.onApprove()
      continue
    }
    when (val body = screen.body) {
      is SuccessBodyModel -> return
      is HardwareConfirmationScreenModel -> body.onConfirm()
      is HardwareConfirmationResultBodyModel -> body.onAcknowledge()
      is AutomaticUiTests -> try {
        body.automateNextPrimaryScreen()
      } catch (e: AutomationUnavailable) {
        throw AssertionError(e.reason, e)
      }
      else -> throw failure("Unable to auto-advance screen with body type ${body::class.simpleName}")
    }
  }

  throw failure("Coroutine completed before advancing could complete")
}
