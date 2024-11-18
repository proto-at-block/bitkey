package build.wallet.integration.statemachine.create

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.CHOOSE_ACCOUNT_ACCESS
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.OverwriteFullAccountCloudBackupWarningModel
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.ui.model.alert.ButtonAlertModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.seconds

class OverwriteFullAccountCloudBackupFunctionalTests : FunSpec({
  test("overwrite full account cloud backup") {
    val uploadCloudBackupApp = launchNewApp()
    uploadCloudBackupApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.CloudStoreAccount1Fake
    )

    val overrideCloudBackupApp = launchNewApp(
      cloudStoreAccountRepository = uploadCloudBackupApp.cloudStoreAccountRepository,
      cloudKeyValueStore = uploadCloudBackupApp.cloudKeyValueStore
    )
    overrideCloudBackupApp.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughCreateKeyboxScreens()
      advanceThroughOnboardKeyboxScreens(listOf(OnboardingKeyboxStep.CloudBackup))
      awaitUntilScreenWithBody<OverwriteFullAccountCloudBackupWarningModel> {
        onOverwriteExistingBackup()
      }

      awaitItem().alertModel.shouldBeTypeOf<ButtonAlertModel>().onPrimaryButtonClick()

      // Uploading cloud backup
      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(CLOUD_SIGN_IN_LOADING) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Cloud backup uploaded
      awaitUntilScreenWithBody<RecoveryChannelsSetupFormBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("cancel overwriting cloud backup") {
    val uploadCloudBackupApp = launchNewApp()
    uploadCloudBackupApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.CloudStoreAccount1Fake
    )

    val overrideCloudBackupApp = launchNewApp(
      cloudStoreAccountRepository = uploadCloudBackupApp.cloudStoreAccountRepository,
      cloudKeyValueStore = uploadCloudBackupApp.cloudKeyValueStore
    )
    overrideCloudBackupApp.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughCreateKeyboxScreens()
      advanceThroughOnboardKeyboxScreens(listOf(OnboardingKeyboxStep.CloudBackup))
      awaitUntilScreenWithBody<OverwriteFullAccountCloudBackupWarningModel> {
        onCancel()
      }

      // Uploading cloud backup
      awaitUntilScreenWithBody<ChooseAccountAccessModel>(CHOOSE_ACCOUNT_ACCESS)

      cancelAndIgnoreRemainingEvents()
    }
  }
})
