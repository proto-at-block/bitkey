package build.wallet.statemachine.onboard

import app.cash.turbine.plusAssign
import bitkey.recovery.DescriptorBackupError
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILED
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_LOADING
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.LOADING_ONBOARDING_STEP
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_ACCOUNT_DESCRIPTOR_BACKUP_FAILURE
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_ACCOUNT_DESCRIPTOR_BACKUP_LOADING
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.SAVE_NOTIFICATIONS_LOADING
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.CloudBackupV2WithLiteAccountMock
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.onboarding.OnboardAccountServiceFake
import build.wallet.onboarding.OnboardAccountStep.CloudBackup
import build.wallet.onboarding.OnboardAccountStep.DescriptorBackup
import build.wallet.onboarding.OnboardAccountStep.NotificationPreferences
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.OnboardFullAccountUiProps
import build.wallet.statemachine.account.create.full.OnboardFullAccountUiStateMachineImpl
import build.wallet.statemachine.account.create.full.onboard.OnboardDescriptorBackupUiProps
import build.wallet.statemachine.account.create.full.onboard.OnboardDescriptorBackupUiStateMachine
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiProps
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachine
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.notifications.NotificationPreferencesProps.Source.Onboarding
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import build.wallet.cloud.backup.CloudBackup as CloudBackupData

class OnboardFullAccountUiStateMachineImplTests : FunSpec({
  val onboardAccountService = OnboardAccountServiceFake()
  val stateMachine = OnboardFullAccountUiStateMachineImpl(
    onboardAccountService = onboardAccountService,
    fullAccountCloudSignInAndBackupUiStateMachine = object :
      FullAccountCloudSignInAndBackupUiStateMachine,
      ScreenStateMachineMock<FullAccountCloudSignInAndBackupProps>(
        id = "cloud-sign-in-and-backup"
      ) {
    },
    notificationPreferencesSetupUiStateMachine = object :
      NotificationPreferencesSetupUiStateMachine,
      ScreenStateMachineMock<NotificationPreferencesSetupUiProps>(
        id = "notification-preferences-setup"
      ) {
    },
    onboardDescriptorBackupUiStateMachine = object :
      OnboardDescriptorBackupUiStateMachine,
      ScreenStateMachineMock<OnboardDescriptorBackupUiProps>(
        id = "onboard-descriptor-backup"
      ) {
    }
  )

  val onFoundLiteAccountWithDifferentId =
    turbines.create<CloudBackupData>("onFoundLiteAccountWithDifferentId")
  val onOverwriteFullAccountCloudBackupWarning =
    turbines.create<Unit>("onOverwriteFullAccountCloudBackupWarning")
  val onOnboardingComplete = turbines.create<Unit>("onOnboardingComplete")

  val props = OnboardFullAccountUiProps(
    isSkipCloudBackupInstructions = false,
    onFoundLiteAccountWithDifferentId = { onFoundLiteAccountWithDifferentId += it },
    onOverwriteFullAccountCloudBackupWarning = { onOverwriteFullAccountCloudBackupWarning += Unit },
    onOnboardingComplete = { onOnboardingComplete += Unit },
    fullAccount = FullAccountMock
  )

  beforeTest {
    onboardAccountService.reset()
    onboardAccountService.setPendingSteps(
      DescriptorBackup(SealedSsekFake),
      CloudBackup(SealedCsekFake),
      NotificationPreferences
    )
  }

  test("complete all onboarding steps") {
    stateMachine.test(props.copy(isSkipCloudBackupInstructions = false)) {

      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Descriptor backup state machine is invoked
      awaitBodyMock<OnboardDescriptorBackupUiProps> {
        fullAccount.shouldBe(FullAccountMock)
        sealedSsek.shouldBe(SealedSsekFake)
        onBackupComplete()
      }

      awaitBody<LoadingSuccessBodyModel>(id = NEW_ACCOUNT_DESCRIPTOR_BACKUP_LOADING)

      onboardAccountService.awaitPendingStep(CloudBackup(SealedCsekFake))

      // Complete cloud backup
      awaitBodyMock<FullAccountCloudSignInAndBackupProps> {
        sealedCsek.shouldBe(SealedCsekFake)
        keybox.shouldBe(KeyboxMock)
        isSkipCloudBackupInstructions.shouldBeFalse()
        onBackupSaved()
      }

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_CLOUD_BACKUP_LOADING)

      // Complete notifications
      onboardAccountService.awaitPendingStep(NotificationPreferences)

      awaitBodyMock<NotificationPreferencesSetupUiProps> {
        accountId.shouldBe(KeyboxMock.fullAccountId)
        source.shouldBe(Onboarding)
        onComplete()
      }

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_NOTIFICATIONS_LOADING)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }

  test("complete cloud backup step only") {
    onboardAccountService.setPendingSteps(CloudBackup(SealedCsekFake))

    stateMachine.test(props.copy(isSkipCloudBackupInstructions = false)) {

      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Complete cloud backup
      awaitBodyMock<FullAccountCloudSignInAndBackupProps> {
        sealedCsek.shouldBe(SealedCsekFake)
        keybox.shouldBe(KeyboxMock)
        isSkipCloudBackupInstructions.shouldBeFalse()
        onBackupSaved()
      }

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_CLOUD_BACKUP_LOADING)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }

  test("complete notifications step only") {
    onboardAccountService.setPendingSteps(NotificationPreferences)

    stateMachine.test(props.copy(isSkipCloudBackupInstructions = true)) {

      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Complete notifications
      onboardAccountService.awaitPendingStep(NotificationPreferences)

      awaitBodyMock<NotificationPreferencesSetupUiProps> {
        accountId.shouldBe(KeyboxMock.fullAccountId)
        source.shouldBe(Onboarding)
        onComplete()
      }

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_NOTIFICATIONS_LOADING)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }

  test("complete cloud backup step - skip cloud backup instructions") {
    onboardAccountService.setPendingSteps(CloudBackup(SealedCsekFake))

    stateMachine.test(props.copy(isSkipCloudBackupInstructions = true)) {

      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Complete cloud backup
      awaitBodyMock<FullAccountCloudSignInAndBackupProps> {
        sealedCsek.shouldBe(SealedCsekFake)
        keybox.shouldBe(KeyboxMock)
        isSkipCloudBackupInstructions.shouldBeTrue()
        onBackupSaved()
      }

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_CLOUD_BACKUP_LOADING)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }

  test("no pending steps") {
    onboardAccountService.setPendingSteps()

    stateMachine.test(props) {
      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }

  test("cloud backup step - overwrite existing account if for the same account ID") {
    onboardAccountService.setPendingSteps(CloudBackup(SealedCsekFake))

    stateMachine.test(props.copy(isSkipCloudBackupInstructions = false)) {

      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Complete cloud backup
      awaitBodyMock<FullAccountCloudSignInAndBackupProps> {
        sealedCsek.shouldBe(SealedCsekFake)
        keybox.shouldBe(KeyboxMock)
        isSkipCloudBackupInstructions.shouldBeFalse()
        // Found existing cloud backup with same account ID
        onExistingAppDataFound.shouldNotBeNull().invoke(
          CloudBackupV2WithFullAccountMock.copy(accountId = KeyboxMock.fullAccountId.serverId)
        ) {
          launch {
            // proceed callback: save backup to mimic cloud backup SM implementation.
            onBackupSaved()
          }
        }
      }

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_CLOUD_BACKUP_LOADING)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }

  test("cloud backup step - found existing lite account cloud backup") {
    onboardAccountService.setPendingSteps(CloudBackup(SealedCsekFake))

    stateMachine.test(props.copy(isSkipCloudBackupInstructions = false)) {

      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      val liteAccountBackup = CloudBackupV2WithLiteAccountMock.copy(accountId = "lite-account-id")

      // Complete cloud backup
      awaitBodyMock<FullAccountCloudSignInAndBackupProps> {
        sealedCsek.shouldBe(SealedCsekFake)
        keybox.shouldBe(KeyboxMock)
        isSkipCloudBackupInstructions.shouldBeFalse()
        // Found existing lite account cloud backup
        onExistingAppDataFound.shouldNotBeNull().invoke(liteAccountBackup) {
          launch {
            // proceed callback: save backup to mimic cloud backup SM implementation.
            onBackupSaved()
          }
        }
      }

      onFoundLiteAccountWithDifferentId.awaitItem().shouldBe(liteAccountBackup)
      onboardAccountService.awaitPendingStep(DescriptorBackup(null))
    }
  }

  test("cloud backup step - found existing full account backup different account ID, show warning") {
    onboardAccountService.setPendingSteps(CloudBackup(SealedCsekFake))

    stateMachine.test(props.copy(isSkipCloudBackupInstructions = false)) {

      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Complete cloud backup
      awaitBodyMock<FullAccountCloudSignInAndBackupProps> {
        sealedCsek.shouldBe(SealedCsekFake)
        keybox.shouldBe(KeyboxMock)
        isSkipCloudBackupInstructions.shouldBeFalse()
        // Found existing full account cloud backup with different account ID
        onExistingAppDataFound.shouldNotBeNull().invoke(
          CloudBackupV2WithFullAccountMock.copy(accountId = "different-account-id")
        ) {
          launch {
            // proceed callback: save backup to mimic cloud backup SM implementation.
            onBackupSaved()
          }
        }
      }

      onOverwriteFullAccountCloudBackupWarning.awaitItem()
    }
  }

  test("cloud backup step - found existing full account backup different account ID, skip showing warning") {
    onboardAccountService.setPendingSteps(CloudBackup(SealedCsekFake))

    stateMachine.test(props.copy(isSkipCloudBackupInstructions = true)) {

      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Complete cloud backup
      awaitBodyMock<FullAccountCloudSignInAndBackupProps> {
        sealedCsek.shouldBe(SealedCsekFake)
        keybox.shouldBe(KeyboxMock)
        isSkipCloudBackupInstructions.shouldBeTrue()
        // Found existing full account cloud backup with different account ID
        onExistingAppDataFound.shouldNotBeNull().invoke(
          CloudBackupV2WithFullAccountMock.copy(accountId = "different-account-id")
        ) {
          launch {
            // proceed callback: save backup to mimic cloud backup SM implementation.
            onBackupSaved()
          }
        }
      }

      onOverwriteFullAccountCloudBackupWarning.expectNoEvents()

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_CLOUD_BACKUP_LOADING)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }

  test("cloud backup step - failed to save backup") {
    onboardAccountService.setPendingSteps(CloudBackup(SealedCsekFake))

    stateMachine.test(props.copy(isSkipCloudBackupInstructions = false)) {

      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Complete cloud backup
      awaitBodyMock<FullAccountCloudSignInAndBackupProps> {
        sealedCsek.shouldBe(SealedCsekFake)
        keybox.shouldBe(KeyboxMock)
        isSkipCloudBackupInstructions.shouldBeFalse()
        onBackupFailed(Error("cloud backup error"))
      }

      awaitBody<FormBodyModel>(id = SAVE_CLOUD_BACKUP_FAILED) {
        // Cloud backup step is not complete
        onboardAccountService.awaitPendingStep(CloudBackup(SealedCsekFake))

        // Retry
        clickPrimaryButton()
      }

      awaitBodyMock<FullAccountCloudSignInAndBackupProps> {
        sealedCsek.shouldBe(SealedCsekFake)
        keybox.shouldBe(KeyboxMock)
        isSkipCloudBackupInstructions.shouldBeFalse()
        // Succeed this time
        onBackupSaved()
      }

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_CLOUD_BACKUP_LOADING)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }

  test("cloud backup step - error completing") {
    onboardAccountService.setPendingSteps(CloudBackup(SealedCsekFake))

    stateMachine.test(props.copy(isSkipCloudBackupInstructions = false)) {

      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Complete cloud backup
      awaitBodyMock<FullAccountCloudSignInAndBackupProps> {
        sealedCsek.shouldBe(SealedCsekFake)
        keybox.shouldBe(KeyboxMock)
        isSkipCloudBackupInstructions.shouldBeFalse()
        onboardAccountService.completeStepError = Error("error completing cloud backup")
        onBackupSaved()
      }

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_CLOUD_BACKUP_LOADING)

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Error completing onboarding")
        // Succeed this time
        onboardAccountService.completeStepError = null
        // Retry
        clickPrimaryButton()
      }

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_CLOUD_BACKUP_LOADING)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }

  test("notifications step - error completing") {
    onboardAccountService.setPendingSteps(NotificationPreferences)

    stateMachine.testWithVirtualTime(props) {

      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Complete notifications
      onboardAccountService.awaitPendingStep(NotificationPreferences)

      awaitBodyMock<NotificationPreferencesSetupUiProps> {
        accountId.shouldBe(KeyboxMock.fullAccountId)
        source.shouldBe(Onboarding)
        onboardAccountService.completeStepError = Error("error completing notifications")
        onComplete()
      }

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_NOTIFICATIONS_LOADING)

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Error completing onboarding")
        // Succeed this time
        onboardAccountService.completeStepError = null
        // Retry
        clickPrimaryButton()
      }

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_NOTIFICATIONS_LOADING)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }

  test("complete descriptor backup step only") {
    onboardAccountService.setPendingSteps(DescriptorBackup(SealedSsekFake))

    stateMachine.test(props) {

      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Descriptor backup state machine is invoked
      awaitBodyMock<OnboardDescriptorBackupUiProps> {
        fullAccount.shouldBe(FullAccountMock)
        sealedSsek.shouldBe(SealedSsekFake)
        onBackupComplete()
      }

      awaitBody<LoadingSuccessBodyModel>(id = NEW_ACCOUNT_DESCRIPTOR_BACKUP_LOADING)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }

  test("descriptor backup step - failed to upload backup") {
    onboardAccountService.setPendingSteps(DescriptorBackup(SealedSsekFake))

    stateMachine.test(props) {
      // Loading initial onboarding state
      awaitBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Descriptor backup state machine reports failure
      awaitBodyMock<OnboardDescriptorBackupUiProps> {
        fullAccount.shouldBe(FullAccountMock)
        sealedSsek.shouldBe(SealedSsekFake)
        val error =
          DescriptorBackupError.NetworkError(RuntimeException("Failed to upload descriptor backup"))
        onBackupFailed(error)
      }

      awaitUntilBody<FormBodyModel>(id = NEW_ACCOUNT_DESCRIPTOR_BACKUP_FAILURE) {
        header.shouldNotBeNull().headline.shouldBe("Error setting up wallet backup")
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")

        // Retry
        clickPrimaryButton()
      }

      // Descriptor backup state machine is invoked again and succeeds
      awaitBodyMock<OnboardDescriptorBackupUiProps> {
        fullAccount.shouldBe(FullAccountMock)
        sealedSsek.shouldBe(SealedSsekFake)
        onBackupComplete()
      }

      awaitBody<LoadingSuccessBodyModel>(id = NEW_ACCOUNT_DESCRIPTOR_BACKUP_LOADING)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }
})
