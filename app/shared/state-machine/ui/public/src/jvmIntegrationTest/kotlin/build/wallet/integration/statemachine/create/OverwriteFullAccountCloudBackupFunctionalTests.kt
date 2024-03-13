package build.wallet.integration.statemachine.create

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.CHOOSE_ACCOUNT_ACCESS
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.NOTIFICATION_PREFERENCES_SETUP
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.testing.launchNewApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class OverwriteFullAccountCloudBackupFunctionalTests : FunSpec({
  test("overwrite full account cloud backup") {
    val uploadCloudBackupApp = launchNewApp()
    uploadCloudBackupApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.CloudStoreAccount1Fake
    )

    val overrideCloudBackupApp = launchNewApp(
      cloudStoreAccountRepository = uploadCloudBackupApp.app.cloudStoreAccountRepository,
      cloudKeyValueStore = uploadCloudBackupApp.app.cloudKeyValueStore
    )
    overrideCloudBackupApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughCreateKeyboxScreens()
      advanceThroughOnboardKeyboxScreens(listOf(OnboardingKeyboxStep.CloudBackup))
      awaitUntilScreenWithBody<FormBodyModel>(OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING) {
        clickPrimaryButton()
      }

      // Uploading cloud backup
      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(CLOUD_SIGN_IN_LOADING) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Cloud backup uploaded
      awaitUntilScreenWithBody<FormBodyModel>(NOTIFICATION_PREFERENCES_SETUP)

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("cancel overwriting cloud backup") {
    val uploadCloudBackupApp = launchNewApp()
    uploadCloudBackupApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.CloudStoreAccount1Fake
    )

    val overrideCloudBackupApp = launchNewApp(
      cloudStoreAccountRepository = uploadCloudBackupApp.app.cloudStoreAccountRepository,
      cloudKeyValueStore = uploadCloudBackupApp.app.cloudKeyValueStore
    )
    overrideCloudBackupApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughCreateKeyboxScreens()
      advanceThroughOnboardKeyboxScreens(listOf(OnboardingKeyboxStep.CloudBackup))
      awaitUntilScreenWithBody<FormBodyModel>(OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING) {
        clickSecondaryButton()
      }

      // Uploading cloud backup
      awaitUntilScreenWithBody<ChooseAccountAccessModel>(CHOOSE_ACCOUNT_ACCESS)

      cancelAndIgnoreRemainingEvents()
    }
  }
})
