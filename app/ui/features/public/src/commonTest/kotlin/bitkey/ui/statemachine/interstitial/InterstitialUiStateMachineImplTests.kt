package bitkey.ui.statemachine.interstitial

import bitkey.account.HardwareType.W1
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.coachmark.CoachmarkServiceMock
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.device.wipe.DeviceWipeEligibilityError
import build.wallet.device.wipe.DeviceWipeEligibilityService
import build.wallet.device.wipe.DeviceWipeEligibilityServiceFake
import build.wallet.device.wipe.InactiveHardwareDevice
import build.wallet.device.wipe.OldW1WipeReadiness
import build.wallet.inheritance.InheritanceUpsellServiceFake
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.inheritance.InheritanceUpsellBodyModel
import build.wallet.statemachine.settings.full.device.wipedevice.WipeContext
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceInitialStep
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceProps
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceUiStateMachine
import build.wallet.statemachine.walletmigration.W3UpgradeBlockerBodyModel
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred

class InterstitialUiStateMachineImplTests : FunSpec({

  val inheritanceUpsellService = InheritanceUpsellServiceFake()
  val coachmarkService = CoachmarkServiceMock(turbineFactory = turbines::create)
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val deviceWipeEligibilityService = DeviceWipeEligibilityServiceFake()
  val wipingDeviceUiStateMachine = object : WipingDeviceUiStateMachine,
    ScreenStateMachineMock<WipingDeviceProps>("wiping-device") {}

  fun stateMachine(
    wipeEligibilityService: DeviceWipeEligibilityService = deviceWipeEligibilityService,
  ) =
    InterstitialUiStateMachineImpl(
      inheritanceUpsellService = inheritanceUpsellService,
      coachmarkService = coachmarkService,
      inAppBrowserNavigator = inAppBrowserNavigator,
      deviceWipeEligibilityService = wipeEligibilityService,
      wipingDeviceUiStateMachine = wipingDeviceUiStateMachine
    )

  val props = InterstitialUiProps(
    account = FullAccountMock,
    isComingFromOnboarding = false
  )

  beforeTest {
    inheritanceUpsellService.reset()
    coachmarkService.resetCoachmarks()
    deviceWipeEligibilityService.reset()
  }

  test("default screen model is null") {
    stateMachine().test(props = props) {
      awaitItem().shouldBeNull()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("inheritance upsell is shown when applicable") {
    stateMachine().test(props = props) {
      // initial loading of the inheritance upsell service
      awaitItem()

      awaitItem().shouldBeScreen()
        .body
        .shouldBeInstanceOf<InheritanceUpsellBodyModel>()
        .onClose()

      awaitItem().shouldBeNull()

      inheritanceUpsellService.shouldShowUpsell().shouldBeFalse()
    }
  }

  test("W3 upgrade blocker CTA opens in-app browser and marks coachmark displayed") {
    coachmarkService.defaultCoachmarks = listOf(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)

    stateMachine().test(props = props) {
      // Initial state before coachmark check completes
      awaitItem().shouldBeNull()

      awaitItem().shouldBeScreen()
        .body
        .shouldBeInstanceOf<W3UpgradeBlockerBodyModel>()
        .onGetStarted()

      coachmarkService.markDisplayedTurbine.awaitItem()
        .shouldBe(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)

      // Should transition to in-app browser state
      awaitUntil {
        it is InterstitialUiModel.Screen && it.screenModel.body is InAppBrowserModel
      }.shouldBeScreen()
        .body
        .shouldBeInstanceOf<InAppBrowserModel>()
        .open()

      inAppBrowserNavigator.onOpenCalls.awaitItem()
        .shouldBe("https://bitkey.world/")

      // Simulate browser close
      inAppBrowserNavigator.onCloseCallback?.invoke()

      awaitUntil { it == null }.shouldBeNull()
    }
  }

  test("W3 upgrade blocker does not show for W3 accounts") {
    coachmarkService.defaultCoachmarks = listOf(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)
    inheritanceUpsellService.markUpsellAsSeen()

    val w3Props = InterstitialUiProps(
      account = FullAccountW3Mock,
      isComingFromOnboarding = false
    )

    stateMachine().test(props = w3Props) {
      awaitItem().shouldBeNull()
      expectNoEvents()
    }
  }

  test("W3 upgrade blocker does not show when coming from onboarding") {
    coachmarkService.defaultCoachmarks = listOf(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)

    val onboardingProps = InterstitialUiProps(
      account = FullAccountMock,
      isComingFromOnboarding = true
    )

    stateMachine().test(props = onboardingProps) {
      awaitItem().shouldBeNull()
    }
  }

  test("no interstitial after onboarding even after async W3 blocker eligibility resolves") {
    // Eligibility check resolves asynchronously; the onboarding gate must continue to suppress
    // the interstitial after that resolution rather than letting the recomposition surface it.
    coachmarkService.defaultCoachmarks = listOf(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)

    val onboardingProps = InterstitialUiProps(
      account = FullAccountMock,
      isComingFromOnboarding = true
    )

    stateMachine().test(props = onboardingProps) {
      // First (and any subsequent) emissions must remain null even after the produceState
      // for shouldShowW3UpgradeBlocker has resolved to true.
      awaitItem().shouldBeNull()
      expectNoEvents()
    }
  }

  test("inheritance upsell does not show immediately after dismissing W3 upgrade blocker") {
    coachmarkService.defaultCoachmarks = listOf(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)
    // Inheritance upsell would otherwise be eligible (fake returns true by default)

    stateMachine().test(props = props) {
      // Initial state before coachmark check completes
      awaitItem().shouldBeNull()

      // W3 upgrade blocker shows first
      awaitItem().shouldBeScreen()
        .body
        .shouldBeInstanceOf<W3UpgradeBlockerBodyModel>()
        .onClose()

      coachmarkService.markDisplayedTurbine.awaitItem()
        .shouldBe(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)

      // After dismissing the blocker, no further interstitial should appear in this session
      awaitItem().shouldBeNull()
    }
  }

  test("W3 upgrade blocker close marks coachmark displayed and dismisses the screen") {
    coachmarkService.defaultCoachmarks = listOf(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)

    stateMachine().test(props = props) {
      // Initial state before coachmark check completes
      awaitItem().shouldBeNull()

      awaitItem().shouldBeScreen()
        .body
        .shouldBeInstanceOf<W3UpgradeBlockerBodyModel>()
        .onClose()

      coachmarkService.markDisplayedTurbine.awaitItem()
        .shouldBe(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)

      awaitItem().shouldBeNull()
    }
  }

  test("old W1 wipe reminder shows for ready W3 accounts") {
    deviceWipeEligibilityService.oldW1WipeReadinessResult = Ok(oldW1ReadyReadiness())

    stateMachine().test(
      props = InterstitialUiProps(
        account = FullAccountW3Mock,
        isComingFromOnboarding = false
      )
    ) {
      awaitItem().shouldBeNull()

      val sheetModel = awaitItem().shouldBeSheet()
      sheetModel.body
        .shouldBeInstanceOf<W3UpgradeOldDeviceWipeReadyBodyModel>()
        .onDone()

      awaitItem().shouldBeNull()
      deviceWipeEligibilityService.markOldW1WipeReminderDismissedCalls.shouldBe(1)
    }

    inheritanceUpsellService.shouldShowUpsell().shouldBeTrue()
  }

  test("old W1 wipe reminder sheet dismiss marks reminder dismissed") {
    deviceWipeEligibilityService.oldW1WipeReadinessResult = Ok(oldW1ReadyReadiness())

    stateMachine().test(
      props = InterstitialUiProps(
        account = FullAccountW3Mock,
        isComingFromOnboarding = false
      )
    ) {
      awaitItem().shouldBeNull()

      awaitItem().shouldBeSheet()
        .onClosed()

      awaitItem().shouldBeNull()
      deviceWipeEligibilityService.markOldW1WipeReminderDismissedCalls.shouldBe(1)
    }
  }

  test("old W1 wipe reminder primary launches wipe flow") {
    deviceWipeEligibilityService.oldW1WipeReadinessResult = Ok(oldW1ReadyReadiness())

    stateMachine().test(
      props = InterstitialUiProps(
        account = FullAccountW3Mock,
        isComingFromOnboarding = false
      )
    ) {
      awaitItem().shouldBeNull()

      awaitItem().shouldBeSheet()
        .body
        .shouldBeInstanceOf<W3UpgradeOldDeviceWipeReadyBodyModel>()
        .onWipeOldDevice()

      val wipeProps = awaitItem().shouldBeScreen()
        .body
        .shouldBeInstanceOf<BodyModelMock<WipingDeviceProps>>()
        .latestProps

      wipeProps.wipeContext.shouldBe(
        WipeContext.InactiveDevice(oldW1Device())
      )
      wipeProps.initialStep.shouldBe(WipingDeviceInitialStep.ScanDevice)
    }
    deviceWipeEligibilityService.markOldW1WipeReminderDismissedCalls.shouldBe(1)
  }

  test("does not show another interstitial when account object refreshes in same session") {
    deviceWipeEligibilityService.oldW1WipeReadinessResult = Ok(oldW1ReadyReadiness())

    stateMachine().test(
      props = InterstitialUiProps(
        account = FullAccountW3Mock,
        isComingFromOnboarding = false
      )
    ) {
      awaitItem().shouldBeNull()

      awaitItem().shouldBeSheet()
        .body
        .shouldBeInstanceOf<W3UpgradeOldDeviceWipeReadyBodyModel>()
        .onDone()

      awaitItem().shouldBeNull()

      updateProps(
        InterstitialUiProps(
          account = FullAccountW3Mock.copy(accountId = FullAccountId("refreshed-account")),
          isComingFromOnboarding = false
        )
      )

      expectNoEvents()
    }
  }

  test("inheritance upsell waits for old W1 readiness to resolve") {
    val oldW1ReadinessResult =
      CompletableDeferred<Result<OldW1WipeReadiness, DeviceWipeEligibilityError>>()
    val delayedDeviceWipeEligibilityService =
      object : DeviceWipeEligibilityService by deviceWipeEligibilityService {
        override suspend fun oldW1WipeReadiness(
          account: FullAccount,
        ): Result<OldW1WipeReadiness, DeviceWipeEligibilityError> {
          return oldW1ReadinessResult.await()
        }
      }

    stateMachine(delayedDeviceWipeEligibilityService).test(
      props = InterstitialUiProps(
        account = FullAccountW3Mock,
        isComingFromOnboarding = false
      )
    ) {
      awaitItem().shouldBeNull()
      expectNoEvents()

      oldW1ReadinessResult.complete(
        Ok(oldW1ReadyReadiness())
      )

      awaitItem().shouldBeSheet()
        .body
        .shouldBeInstanceOf<W3UpgradeOldDeviceWipeReadyBodyModel>()
    }

    inheritanceUpsellService.shouldShowUpsell().shouldBeTrue()
  }
})

private fun oldW1ReadyReadiness() =
  OldW1WipeReadiness.Ready(
    oldW1Device()
  )

private fun oldW1Device() =
  InactiveHardwareDevice(
    hardwareType = W1,
    hardwareFingerprint = "old-fingerprint"
  )

private fun InterstitialUiModel?.shouldBeScreen(): ScreenModel =
  shouldNotBeNull()
    .shouldBeInstanceOf<InterstitialUiModel.Screen>()
    .screenModel

private fun InterstitialUiModel?.shouldBeSheet(): SheetModel =
  shouldNotBeNull()
    .shouldBeInstanceOf<InterstitialUiModel.Sheet>()
    .sheetModel
