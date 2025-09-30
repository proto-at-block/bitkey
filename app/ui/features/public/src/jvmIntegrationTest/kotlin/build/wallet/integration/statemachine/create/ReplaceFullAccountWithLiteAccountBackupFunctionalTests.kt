package build.wallet.integration.statemachine.create

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.relationships.ProtectedCustomerAlias
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.relationships.syncAndVerifyRelationships
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.createTcInvite
import build.wallet.testing.ext.getActiveFullAccount
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.onboardLiteAccountFromInvitation
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class ReplaceFullAccountWithLiteAccountBackupFunctionalTests : FunSpec({
  test("replace full account with lite account backup") {
    val (app, liteAccount, liteBackup) = createLiteAccountWithInvite()

    // Start a new app to attempt to onboard a new full account.
    val onboardApp = launchNewApp(
      cloudStoreAccountRepository = app.cloudStoreAccountRepository,
      cloudKeyValueStore = app.cloudKeyValueStore
    )

    // Sanity check that the cloud backup is available to the app that will now go through onboarding.
    onboardApp.cloudBackupRepository
      .readActiveBackup(
        CloudStoreAccountFake.CloudStoreAccount1Fake
      )
      .getOrThrow()
      .shouldNotBeNull()
      .shouldBe(liteBackup)

    // Set push notifications to authorized to enable us to successfully advance through
    // the notifications step in onboarding.
    onboardApp.pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
      PermissionStatus.Authorized
    )

    onboardApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughCreateKeyboxScreens()
      advanceThroughOnboardKeyboxScreens(listOf(OnboardingKeyboxStep.CloudBackup))
      // Expect the lite account backup to be found and we transition to the
      // [ReplaceWithLiteAccountRestoreUiStateMachine]
      awaitUntilBody<LoadingSuccessBodyModel>(
        CloudEventTrackerScreenId.LOADING_RESTORING_FROM_LITE_ACCOUNT_CLOUD_BACKUP_DURING_FULL_ACCOUNT_ONBOARDING
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      onboardApp.onboardingKeyboxHwAuthPublicKeyDao.get().getOrThrow().shouldNotBeNull()
      advanceThroughOnboardKeyboxScreens(
        listOf(
          OnboardingKeyboxStep.CloudBackup,
          OnboardingKeyboxStep.NotificationPreferences
        ),
        // We skip the backup instructions because this lite account backup and upgrade is
        // completely transparent to the user
        isCloudBackupSkipSignIn = true
      )
      awaitUntilBody<LoadingSuccessBodyModel>(GeneralEventTrackerScreenId.LOADING_SAVING_KEYBOX) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    onboardApp.onboardingKeyboxHwAuthPublicKeyDao.get().getOrThrow().shouldBeNull()
    verifyAccountDataIsPreserved(onboardApp, liteAccount)
  }

  test("relaunch app before backing up upgraded lite account") {
    val (app, liteAccount, liteBackup) = createLiteAccountWithInvite()

    // Start a new app to attempt to onboard a new full account.
    var onboardApp = launchNewApp(
      cloudStoreAccountRepository = app.cloudStoreAccountRepository,
      cloudKeyValueStore = app.cloudKeyValueStore
    )

    // Sanity check that the cloud backup is available to the app that will now go through onboarding.
    onboardApp.cloudBackupRepository
      .readActiveBackup(
        CloudStoreAccountFake.CloudStoreAccount1Fake
      )
      .getOrThrow()
      .shouldNotBeNull()
      .shouldBe(liteBackup)

    onboardApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughCreateKeyboxScreens()
      advanceThroughOnboardKeyboxScreens(listOf(OnboardingKeyboxStep.CloudBackup))
      // Expect the lite account backup to be found and we transition to the
      // [ReplaceWithLiteAccountRestoreUiStateMachine]
      awaitUntilBody<LoadingSuccessBodyModel>(
        CloudEventTrackerScreenId.LOADING_RESTORING_FROM_LITE_ACCOUNT_CLOUD_BACKUP_DURING_FULL_ACCOUNT_ONBOARDING
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<LoadingSuccessBodyModel>(
        CreateAccountEventTrackerScreenId.LOADING_ONBOARDING_STEP
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      // Cancel and exit the app before attempting cloud backup
      // This might be flaky...?
      cancelAndIgnoreRemainingEvents()
    }

    // Restart the app to lose any in-memory state
    onboardApp = onboardApp.relaunchApp()

    // Set push notifications to authorized to enable us to successfully advance through
    // the notifications step in onboarding.
    onboardApp.pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
      PermissionStatus.Authorized
    )
    onboardApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Since the app restarted, we will show the backup instructions.
      advanceThroughOnboardKeyboxScreens(
        listOf(
          OnboardingKeyboxStep.CloudBackup,
          OnboardingKeyboxStep.NotificationPreferences
        )
      )
      awaitUntilBody<LoadingSuccessBodyModel>(GeneralEventTrackerScreenId.LOADING_SAVING_KEYBOX) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    verifyAccountDataIsPreserved(onboardApp, liteAccount)
  }
})

private const val PROTECTED_CUSTOMER_NAME = "protected customer"

private suspend fun TestScope.createLiteAccountWithInvite(): Triple<AppTester, LiteAccount, CloudBackupV2> {
  val fullApp = launchNewApp()
  fullApp.onboardFullAccountWithFakeHardware()
  val liteApp = launchNewApp()

  val (inviteCode, _) = fullApp.createTcInvite("Recovery Contact")
  val liteAccount =
    liteApp.onboardLiteAccountFromInvitation(
      inviteCode,
      PROTECTED_CUSTOMER_NAME
    )

  val liteBackup = liteApp.liteAccountCloudBackupCreator.create(liteAccount).getOrThrow()
  // Note the cloud backup is written to shared settings.
  liteApp.cloudBackupRepository.writeBackup(
    liteAccount.accountId,
    CloudStoreAccountFake.CloudStoreAccount1Fake,
    liteBackup,
    requireAuthRefresh = true
  ).getOrThrow()

  return Triple(liteApp, liteAccount, liteBackup)
}

private suspend fun verifyAccountDataIsPreserved(
  onboardApp: AppTester,
  liteAccount: LiteAccount,
) {
  // Expect the active full account ID and the lite account ID to match
  val onboardedAccount = onboardApp.getActiveFullAccount()
  onboardedAccount.accountId.serverId.shouldBe(liteAccount.accountId.serverId)
  val socRecRelationships = onboardApp.relationshipsService
    .syncAndVerifyRelationships(onboardedAccount)
    .getOrThrow()
  // Expect the protected customer to have been preserved
  socRecRelationships.protectedCustomers.shouldHaveSize(1)
  socRecRelationships.protectedCustomers.first().alias
    .shouldBe(ProtectedCustomerAlias(PROTECTED_CUSTOMER_NAME))
}
