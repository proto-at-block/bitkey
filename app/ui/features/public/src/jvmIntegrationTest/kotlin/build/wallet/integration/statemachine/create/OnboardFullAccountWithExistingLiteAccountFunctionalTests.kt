package build.wallet.integration.statemachine.create

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.LOADING_RESTORING_FROM_LITE_ACCOUNT_CLOUD_BACKUP_DURING_FULL_ACCOUNT_ONBOARDING
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.LOADING_ONBOARDING_STEP
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.LOADING_SAVING_KEYBOX
import build.wallet.bitkey.account.LiteAccount
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.createTcInvite
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.onboardLiteAccountFromInvitation
import build.wallet.testing.ext.testWithTwoApps
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class OnboardFullAccountWithExistingLiteAccountFunctionalTests : FunSpec({

  testWithTwoApps("onboard full account from sign in page with existing lite account in cloud backup") { protectedCustomerApp, liteApp ->
    // Step 1: Create a lite account with a protected customer relationship
    val (liteAccount, liteBackup) = createLiteAccountWithBackup(protectedCustomerApp, liteApp)

    // Step 2: Start a new app to onboard a full account, simulating a different user
    val onboardApp = launchNewApp(
      cloudStoreAccountRepository = liteApp.cloudStoreAccountRepository,
      cloudKeyValueStore = liteApp.cloudKeyValueStore
    )

    // Verify the lite account backup is present in cloud
    onboardApp.cloudBackupRepository
      .readActiveBackup(CloudStoreAccountFake.CloudStoreAccount1Fake)
      .getOrThrow()
      .shouldNotBeNull()
      .shouldBe(liteBackup)

    // Set push notifications to authorized to enable us to successfully advance through
    // the notifications step in onboarding.
    onboardApp.pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
      PermissionStatus.Authorized
    )

    // Step 3: Attempt to onboard a full account - should encounter the lite account backup
    onboardApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughCreateKeyboxScreens()

      // Wait for the initial onboarding state to load
      awaitUntilBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Start cloud backup step - show backup instructions
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()

      // Sign in to cloud - this will discover the existing lite account backup
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccountFake.CloudStoreAccount1Fake)

      // When the cloud backup state machine discovers a lite account backup with a different
      // account ID, it triggers the onFoundLiteAccountWithDifferentId callback which
      // transitions to ReplaceWithLiteAccountRestoreUiStateMachine.
      // This is validated by observing the loading screen:
      awaitUntilBody<LoadingSuccessBodyModel>(
        LOADING_RESTORING_FROM_LITE_ACCOUNT_CLOUD_BACKUP_DURING_FULL_ACCOUNT_ONBOARDING
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Complete the remaining onboarding steps (cloud backup + notifications)
      advanceThroughOnboardKeyboxScreens(
        listOf(
          OnboardingKeyboxStep.CloudBackup,
          OnboardingKeyboxStep.NotificationPreferences
        ),
        // We skip the backup instructions because this lite account backup and upgrade is
        // completely transparent to the user
        isCloudBackupSkipSignIn = true
      )

      // Wait for final keybox saving
      awaitUntilBody<LoadingSuccessBodyModel>(LOADING_SAVING_KEYBOX) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Verify we reach the home screen successfully
      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    // Verify the lite account data is preserved and accessible
    liteAccount.accountId.serverId.shouldBe(liteBackup.accountId)
  }
})

private const val PROTECTED_CUSTOMER_NAME = "protected customer"

/**
 * Creates a lite account with an invitation from a protected customer and writes
 * the lite account backup to cloud storage.
 */
private suspend fun createLiteAccountWithBackup(
  protectedCustomerApp: AppTester,
  liteApp: AppTester,
): Pair<LiteAccount, CloudBackupV2> {
  // Create protected customer account
  protectedCustomerApp.onboardFullAccountWithFakeHardware()

  // Create trusted contact invitation
  val (inviteCode, _) = protectedCustomerApp.createTcInvite("Recovery Contact")

  // Onboard lite account from invitation
  val liteAccount = liteApp.onboardLiteAccountFromInvitation(
    inviteCode,
    PROTECTED_CUSTOMER_NAME
  )

  // Create and write lite account backup to cloud
  val liteBackup = liteApp.liteAccountCloudBackupCreator.create(liteAccount).getOrThrow()
  liteApp.cloudBackupRepository.writeBackup(
    liteAccount.accountId,
    CloudStoreAccountFake.CloudStoreAccount1Fake,
    liteBackup,
    requireAuthRefresh = true
  ).getOrThrow()

  return liteAccount to liteBackup
}
