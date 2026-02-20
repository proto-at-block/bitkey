package build.wallet.statemachine.root

import app.cash.turbine.plusAssign
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_KEY_MISSING
import build.wallet.availability.AgeRangeVerificationResult
import build.wallet.availability.AgeRangeVerificationServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.ChooseAccountAccessUiProps
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachine
import build.wallet.statemachine.core.AgeRestrictedBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiProps
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachine
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachine
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachineProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.toImmutableList

class NoActiveAccountUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val ageRangeVerificationService = AgeRangeVerificationServiceFake()
  val recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create)
  val accountService = AccountServiceFake()
  val deviceInfoProvider = DeviceInfoProviderMock()

  val chooseAccountAccessUiStateMachine =
    object : ChooseAccountAccessUiStateMachine,
      ScreenStateMachineMock<ChooseAccountAccessUiProps>(id = "choose-account-access") {}
  val accessCloudBackupUiStateMachine =
    object : AccessCloudBackupUiStateMachine,
      ScreenStateMachineMock<AccessCloudBackupUiProps>(id = "access-cloud-backup") {}
  val lostAppRecoveryUiStateMachine =
    object : LostAppRecoveryUiStateMachine,
      ScreenStateMachineMock<LostAppRecoveryUiProps>(id = "lost-app-recovery") {}
  val emergencyExitKitRecoveryUiStateMachine =
    object : EmergencyExitKitRecoveryUiStateMachine,
      ScreenStateMachineMock<EmergencyExitKitRecoveryUiStateMachineProps>(
        id = "emergency-exit-kit-recovery"
      ) {}

  val onViewFullAccountCalls = turbines.create<Unit>("onViewFullAccount calls")
  val goToLiteAccountCreationCalls = turbines.create<Unit>("goToLiteAccountCreation calls")
  val onSoftwareWalletCreatedCalls = turbines.create<Unit>("onSoftwareWalletCreated calls")
  val onStartLiteAccountRecoveryCalls = turbines.create<Unit>("onStartLiteAccountRecovery calls")
  val onStartLiteAccountCreationCalls = turbines.create<Unit>("onStartLiteAccountCreation calls")
  val onCreateFullAccountCalls = turbines.create<Unit>("onCreateFullAccount calls")

  val props = NoActiveAccountUiProps(
    goToLiteAccountCreation = { goToLiteAccountCreationCalls += Unit },
    onSoftwareWalletCreated = { onSoftwareWalletCreatedCalls += Unit },
    onStartLiteAccountRecovery = { onStartLiteAccountRecoveryCalls += Unit },
    onStartLiteAccountCreation = { _, _ -> onStartLiteAccountCreationCalls += Unit },
    onCreateFullAccount = { onCreateFullAccountCalls += Unit },
    onViewFullAccount = { onViewFullAccountCalls += Unit }
  )

  lateinit var stateMachine: NoActiveAccountUiStateMachineImpl

  beforeTest {
    ageRangeVerificationService.reset()
    recoveryStatusService.reset()
    accountService.reset()
    deviceInfoProvider.reset()
    Router.reset()

    stateMachine = NoActiveAccountUiStateMachineImpl(
      lostAppRecoveryUiStateMachine = lostAppRecoveryUiStateMachine,
      chooseAccountAccessUiStateMachine = chooseAccountAccessUiStateMachine,
      accessCloudBackupUiStateMachine = accessCloudBackupUiStateMachine,
      emergencyExitKitRecoveryUiStateMachine = emergencyExitKitRecoveryUiStateMachine,
      accountService = accountService,
      deviceInfoProvider = deviceInfoProvider,
      ageRangeVerificationService = ageRangeVerificationService,
      eventTracker = eventTracker,
      recoveryStatusService = recoveryStatusService
    )
  }

  suspend fun awaitAnalyticsEvent() {
    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_OPEN_KEY_MISSING)
    )
  }

  test("shows loading screen while age verification is pending") {
    ageRangeVerificationService.result = AgeRangeVerificationResult.Allowed

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps>()
      awaitAnalyticsEvent()
    }
  }

  test("shows age restricted screen when age verification is denied") {
    ageRangeVerificationService.result = AgeRangeVerificationResult.Denied

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBody<AgeRestrictedBodyModel>()
      awaitAnalyticsEvent()
    }
  }

  test("shows choose account access screen when age verification is allowed") {
    ageRangeVerificationService.result = AgeRangeVerificationResult.Allowed

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps>()
      awaitAnalyticsEvent()
    }
  }

  test("shows recovery screen when recovery is already in progress") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock

    stateMachine.test(props) {
      awaitBodyMock<LostAppRecoveryUiProps> {
        activeRecovery.shouldBe(StillRecoveringInitiatedRecoveryMock)
      }
      awaitAnalyticsEvent()
    }
  }

  test("transitions to checking cloud backup when starting recovery") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onStartRecovery()
      }
      awaitAnalyticsEvent()

      awaitBodyMock<AccessCloudBackupUiProps> {
        startIntent.shouldBe(StartIntent.RestoreBitkey)
        showErrorOnBackupMissing.shouldBe(true)
      }
    }
  }

  test("transitions to checking cloud backup when starting lite account creation") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onStartLiteAccountCreation()
      }
      awaitAnalyticsEvent()

      awaitBodyMock<AccessCloudBackupUiProps> {
        startIntent.shouldBe(StartIntent.BeTrustedContact)
        showErrorOnBackupMissing.shouldBe(false)
      }
    }
  }

  test("transitions to emergency exit recovery when starting EEK recovery") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onStartEmergencyExitRecovery()
      }
      awaitAnalyticsEvent()

      awaitBodyMock<EmergencyExitKitRecoveryUiStateMachineProps>()
    }
  }

  test("returns to getting started when exiting cloud backup check") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onStartRecovery()
      }
      awaitAnalyticsEvent()

      awaitBodyMock<AccessCloudBackupUiProps> {
        onExit()
      }

      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps>()
    }
  }

  test("transitions to full account recovery from cloud backup") {
    val cloudBackups = listOf(CloudBackupV2WithFullAccountMock).toImmutableList()

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onStartRecovery()
      }
      awaitAnalyticsEvent()

      awaitBodyMock<AccessCloudBackupUiProps> {
        onStartCloudRecovery(CloudAccountMock("test-account"), cloudBackups)
      }

      awaitBodyMock<LostAppRecoveryUiProps> {
        this.cloudBackups.shouldBe(cloudBackups)
        activeRecovery.shouldBe(null)
      }
    }
  }

  test("transitions to lost app recovery when no cloud backup found") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onStartRecovery()
      }
      awaitAnalyticsEvent()

      awaitBodyMock<AccessCloudBackupUiProps> {
        onStartLostAppRecovery()
      }

      awaitBodyMock<LostAppRecoveryUiProps> {
        cloudBackups.shouldBe(emptyList())
        activeRecovery.shouldBe(null)
      }
    }
  }

  test("returns to getting started when rolling back from recovery") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onStartRecovery()
      }
      awaitAnalyticsEvent()

      awaitBodyMock<AccessCloudBackupUiProps> {
        onStartLostAppRecovery()
      }

      awaitBodyMock<LostAppRecoveryUiProps> {
        onRollback()
      }

      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps>()
    }
  }

  test("returns to getting started when exiting emergency exit kit recovery") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onStartEmergencyExitRecovery()
      }
      awaitAnalyticsEvent()

      awaitBodyMock<EmergencyExitKitRecoveryUiStateMachineProps> {
        onExit()
      }

      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps>()
    }
  }

  test("transitions to emergency exit recovery from cloud backup check") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onStartRecovery()
      }
      awaitAnalyticsEvent()

      awaitBodyMock<AccessCloudBackupUiProps> {
        onImportEmergencyExitKit()
      }

      awaitBodyMock<EmergencyExitKitRecoveryUiStateMachineProps>()
    }
  }

  test("handles TrustedContactInvite deep link") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps>()
      awaitAnalyticsEvent()

      Router.route = Route.TrustedContactInvite(inviteCode = "test-invite-code")

      awaitBodyMock<AccessCloudBackupUiProps> {
        startIntent.shouldBe(StartIntent.BeTrustedContact)
        inviteCode.shouldBe("test-invite-code")
      }
    }
  }

  test("handles BeneficiaryInvite deep link") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps>()
      awaitAnalyticsEvent()

      Router.route = Route.BeneficiaryInvite(inviteCode = "beneficiary-invite-code")

      awaitBodyMock<AccessCloudBackupUiProps> {
        startIntent.shouldBe(StartIntent.BeBeneficiary)
        inviteCode.shouldBe("beneficiary-invite-code")
      }
    }
  }

  test("ignores deep link when not in getting started state") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onStartRecovery()
      }
      awaitAnalyticsEvent()

      awaitBodyMock<AccessCloudBackupUiProps> {
        startIntent.shouldBe(StartIntent.RestoreBitkey)
      }

      // Try to set a deep link - should be ignored since we're in CheckingCloudBackup state
      Router.route = Route.TrustedContactInvite(inviteCode = "ignored-code")

      expectNoEvents()
    }
  }

  test("calls onViewFullAccount when full account becomes active") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps>()
      awaitAnalyticsEvent()

      // Simulate account becoming active
      accountService.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))

      onViewFullAccountCalls.awaitItem()
    }
  }

  test("calls goToLiteAccountCreation from recovery screen") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock

    stateMachine.test(props) {
      awaitBodyMock<LostAppRecoveryUiProps> {
        goToLiteAccountCreation()
      }
      awaitAnalyticsEvent()

      goToLiteAccountCreationCalls.awaitItem()
    }
  }

  test("forwards onSoftwareWalletCreated callback through choose account access") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onSoftwareWalletCreated(SoftwareAccountMock)
      }
      awaitAnalyticsEvent()

      onSoftwareWalletCreatedCalls.awaitItem()
    }
  }

  test("forwards onCreateFullAccount callback through choose account access") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onCreateFullAccount()
      }
      awaitAnalyticsEvent()

      onCreateFullAccountCalls.awaitItem()
    }
  }

  test("forwards onStartLiteAccountCreation callback through cloud backup check") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onStartLiteAccountCreation()
      }
      awaitAnalyticsEvent()

      awaitBodyMock<AccessCloudBackupUiProps> {
        onStartLiteAccountCreation("test-invite", StartIntent.BeTrustedContact)
      }

      onStartLiteAccountCreationCalls.awaitItem()
    }
  }

  test("forwards onStartLiteAccountRecovery callback through cloud backup check") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<ChooseAccountAccessUiProps> {
        onStartLiteAccountCreation()
      }
      awaitAnalyticsEvent()

      awaitBodyMock<AccessCloudBackupUiProps> {
        onStartLiteAccountRecovery(CloudBackupV2WithFullAccountMock)
      }

      onStartLiteAccountRecoveryCalls.awaitItem()
    }
  }

  test("cold start deep link is handled when route is already set") {
    // Set route before starting state machine (simulates cold start)
    Router.route = Route.TrustedContactInvite(inviteCode = "cold-start-code")

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      awaitBodyMock<AccessCloudBackupUiProps> {
        startIntent.shouldBe(StartIntent.BeTrustedContact)
        inviteCode.shouldBe("cold-start-code")
      }
      awaitAnalyticsEvent()
    }
  }
})
