package build.wallet.integration.statemachine.inheritance.robots

import app.cash.turbine.ReceiveTurbine
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.feature.setFlagValue
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.input.NameInputBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.inheritance.InheritanceManagementUiProps
import build.wallet.statemachine.inheritance.ManagingInheritanceBodyModel
import build.wallet.statemachine.inheritance.ManagingInheritanceTab
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.recovery.socrec.add.SaveContactBodyModel
import build.wallet.statemachine.trustedcontact.model.EnteringBenefactorNameBodyModel
import build.wallet.statemachine.trustedcontact.model.EnteringInviteCodeBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.robots.advanceUntilScreenWithBody
import build.wallet.statemachine.ui.robots.clickRecoveryContacts
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import io.kotest.core.test.TestScope
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

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
): InheritanceTestApps {
  val benefactorApp = launchNewApp(executeWorkers = false)
  benefactorApp.inheritanceUseEncryptedDescriptorFeatureFlag.setFlagValue(true)
  benefactorApp.onboardFullAccountWithFakeHardware(
    cloudStoreAccountForBackup = CloudStoreAccountFake.ProtectedCustomerFake
  )
  val beneficiaryApp = launchNewApp(cloudKeyValueStore = benefactorApp.cloudKeyValueStore)
  beneficiaryApp.inheritanceUseEncryptedDescriptorFeatureFlag.setFlagValue(true)
  beneficiaryApp.onboardFullAccountWithFakeHardware(
    cloudStoreAccountForBackup = CloudStoreAccountFake.TrustedContactFake
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
    advanceUntilScreenWithBody<SuccessBodyModel>()
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
 * Advances through the full account invite screens for a TC receiving an
 * inheritance invite.
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceThroughFullAccountAcceptTCInviteScreens(
  inviteCode: String,
  protectedCustomerAlias: String,
) {
  awaitUntilBody<MoneyHomeBodyModel>()
    .onSecurityHubTabClick()

  awaitUntilBody<SecurityHubBodyModel>()
    .clickRecoveryContacts()

  awaitUntilBody<FormBodyModel> {
    header?.headline.shouldBe("Recovery Contacts")
    mainContentList.shouldHaveSize(2) // 1 list for TCs, 1 for protected customers
      .toList()[1]
      .shouldBeInstanceOf<FormMainContentModel.ListGroup>()
      .listGroupModel
      .footerButton
      .shouldNotBeNull()
      .onClick()
  }

  awaitUntilBody<EnteringInviteCodeBodyModel> {
    onValueChange(inviteCode)
  }

  advanceUntilScreenWithBody<EnteringBenefactorNameBodyModel> {
    onValueChange(protectedCustomerAlias)
  }

  advanceUntilScreenWithBody<SuccessBodyModel>()
}
