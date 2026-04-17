package build.wallet.integration.statemachine.create

import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.LOADING_SAVING_KEYBOX
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.CHOOSE_ACCOUNT_ACCESS
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.feature.setFlagValue
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.onboarding.OnboardingKeyboxStep.BuildHardwareDescriptor
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.OverwriteFullAccountCloudBackupWarningModel
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.HardwareCoverageMode
import build.wallet.testing.ext.assertActiveHardwareType
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.testForHardwareHappyPaths
import build.wallet.ui.model.alert.ButtonAlertModel
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class OverwriteFullAccountCloudBackupFunctionalTests : FunSpec({
  testForHardwareHappyPaths("overwrite full account cloud backup") { _, coverageMode ->
    val uploadCloudBackupApp = launchNewApp()
    uploadCloudBackupApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.CloudStoreAccount1Fake,
      hardwareType = coverageMode.hardwareType
    )

    val overrideCloudBackupApp = launchNewApp(
      cloudStoreAccountRepository = uploadCloudBackupApp.cloudStoreAccountRepository,
      cloudBackupStore = uploadCloudBackupApp.cloudBackupStore
    )
    overrideCloudBackupApp.accountConfigService.setHardwareType(coverageMode.hardwareType).getOrThrow()
    overrideCloudBackupApp.w3OnboardingFeatureFlag.setFlagValue(coverageMode == HardwareCoverageMode.W3Private)
    overrideCloudBackupApp.pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
      PermissionStatus.Authorized
    )
    overrideCloudBackupApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughCreateKeyboxScreens(coverageMode)
      advanceThroughOnboardKeyboxScreens(listOf(OnboardingKeyboxStep.CloudBackup))
      awaitUntilBody<OverwriteFullAccountCloudBackupWarningModel> {
        onOverwriteExistingBackup()
      }

      awaitItem().alertModel.shouldBeTypeOf<ButtonAlertModel>().onPrimaryButtonClick()

      // Uploading cloud backup
      awaitUntilBody<LoadingSuccessBodyModel>(CLOUD_SIGN_IN_LOADING) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      advanceThroughOnboardKeyboxScreens(
        buildList {
          add(NotificationPreferences)
          if (coverageMode == HardwareCoverageMode.W3Private) {
            add(BuildHardwareDescriptor)
          }
        },
        hardwareType = coverageMode.hardwareType
      )
      awaitUntilBody<LoadingSuccessBodyModel>(LOADING_SAVING_KEYBOX) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    overrideCloudBackupApp.assertActiveHardwareType(coverageMode.hardwareType)
  }

  testForHardwareHappyPaths("cancel overwriting cloud backup") { _, coverageMode ->
    val uploadCloudBackupApp = launchNewApp()
    uploadCloudBackupApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccountFake.CloudStoreAccount1Fake,
      hardwareType = coverageMode.hardwareType
    )

    val overrideCloudBackupApp = launchNewApp(
      cloudStoreAccountRepository = uploadCloudBackupApp.cloudStoreAccountRepository,
      cloudBackupStore = uploadCloudBackupApp.cloudBackupStore
    )
    overrideCloudBackupApp.accountConfigService.setHardwareType(coverageMode.hardwareType).getOrThrow()
    overrideCloudBackupApp.w3OnboardingFeatureFlag.setFlagValue(coverageMode == HardwareCoverageMode.W3Private)
    overrideCloudBackupApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      advanceThroughCreateKeyboxScreens(coverageMode)
      advanceThroughOnboardKeyboxScreens(listOf(OnboardingKeyboxStep.CloudBackup))
      awaitUntilBody<OverwriteFullAccountCloudBackupWarningModel> {
        onCancel()
      }

      if (coverageMode == HardwareCoverageMode.W3Private) {
        // W3 cancel triggers NFC session for account deletion; approve the emulated prompt
        // and confirm the second tap.
        awaitUntilScreenWithBody<BodyModel>(
          matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
        ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
        awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
      }

      awaitUntilBody<ChooseAccountAccessModel>(CHOOSE_ACCOUNT_ACCESS)

      cancelAndIgnoreRemainingEvents()
    }

    overrideCloudBackupApp.accountService.activeAccount().first().shouldBeNull()
    overrideCloudBackupApp.keyboxDao.onboardingKeybox().first().getOrThrow().shouldBeNull()
    overrideCloudBackupApp.onboardingKeyboxHwAuthPublicKeyDao.get().getOrThrow().shouldBeNull()
    overrideCloudBackupApp.onboardingKeyboxSealedSsekDao.get().getOrThrow().shouldBeNull()
  }
})
