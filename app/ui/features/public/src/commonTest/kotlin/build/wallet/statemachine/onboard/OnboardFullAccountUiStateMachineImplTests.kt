package build.wallet.statemachine.onboard

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILED
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_LOADING
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.LOADING_ONBOARDING_STEP
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.SAVE_NOTIFICATIONS_LOADING
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.CloudBackupV2WithLiteAccountMock
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.onboarding.OnboardAccountServiceFake
import build.wallet.onboarding.OnboardAccountStep
import build.wallet.onboarding.OnboardAccountStep.NotificationPreferences
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.OnboardFullAccountUiProps
import build.wallet.statemachine.account.create.full.OnboardFullAccountUiStateMachineImpl
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
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

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
    }
  )

  val onFoundLiteAccountWithDifferentId =
    turbines.create<CloudBackupV2>("onFoundLiteAccountWithDifferentId")
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
      OnboardAccountStep.CloudBackup(SealedCsekFake),
      NotificationPreferences
    )
  }

  test("complete all onboarding steps") {
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
    onboardAccountService.setPendingSteps(OnboardAccountStep.CloudBackup(SealedCsekFake))

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
    onboardAccountService.setPendingSteps(OnboardAccountStep.CloudBackup(SealedCsekFake))

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
    onboardAccountService.setPendingSteps(OnboardAccountStep.CloudBackup(SealedCsekFake))

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
          // proceed callback: save backup to mimic cloud backup SM implementation.
          onBackupSaved()
        }
      }

      awaitBody<LoadingSuccessBodyModel>(id = SAVE_CLOUD_BACKUP_LOADING)

      // Onboarding is complete
      onboardAccountService.awaitPendingStep(null)
      onOnboardingComplete.awaitItem()
    }
  }

  test("cloud backup step - found existing lite account cloud backup") {
    onboardAccountService.setPendingSteps(OnboardAccountStep.CloudBackup(SealedCsekFake))

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
          // proceed callback: save backup to mimic cloud backup SM implementation.
          onBackupSaved()
        }
      }

      onFoundLiteAccountWithDifferentId.awaitItem().shouldBe(liteAccountBackup)
    }
  }

  test("cloud backup step - found existing full account backup different account ID, show warning") {
    onboardAccountService.setPendingSteps(OnboardAccountStep.CloudBackup(SealedCsekFake))

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
          // proceed callback: save backup to mimic cloud backup SM implementation.
          onBackupSaved()
        }
      }

      onOverwriteFullAccountCloudBackupWarning.awaitItem()
    }
  }

  test("cloud backup step - found existing full account backup different account ID, skip showing warning") {
    onboardAccountService.setPendingSteps(OnboardAccountStep.CloudBackup(SealedCsekFake))

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
          // proceed callback: save backup to mimic cloud backup SM implementation.
          onBackupSaved()
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
    onboardAccountService.setPendingSteps(OnboardAccountStep.CloudBackup(SealedCsekFake))

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
        onboardAccountService.awaitPendingStep(OnboardAccountStep.CloudBackup(SealedCsekFake))

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
    onboardAccountService.setPendingSteps(OnboardAccountStep.CloudBackup(SealedCsekFake))

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
})
