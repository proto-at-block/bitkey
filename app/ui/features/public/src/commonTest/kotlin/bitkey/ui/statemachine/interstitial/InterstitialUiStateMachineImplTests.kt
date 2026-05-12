package bitkey.ui.statemachine.interstitial

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.coachmark.CoachmarkServiceMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.inheritance.InheritanceUpsellServiceFake
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.inheritance.InheritanceUpsellBodyModel
import build.wallet.statemachine.walletmigration.W3UpgradeBlockerBodyModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class InterstitialUiStateMachineImplTests : FunSpec({

  val inheritanceUpsellService = InheritanceUpsellServiceFake()
  val coachmarkService = CoachmarkServiceMock(turbineFactory = turbines::create)
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

  fun stateMachine() =
    InterstitialUiStateMachineImpl(
      inheritanceUpsellService = inheritanceUpsellService,
      coachmarkService = coachmarkService,
      inAppBrowserNavigator = inAppBrowserNavigator
    )

  val props = InterstitialUiProps(
    account = FullAccountMock,
    isComingFromOnboarding = false
  )

  beforeTest {
    inheritanceUpsellService.reset()
    coachmarkService.resetCoachmarks()
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

      awaitItem().shouldNotBeNull()
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

      awaitItem().shouldNotBeNull()
        .body
        .shouldBeInstanceOf<W3UpgradeBlockerBodyModel>()
        .onGetStarted()

      coachmarkService.markDisplayedTurbine.awaitItem()
        .shouldBe(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)

      // Should transition to in-app browser state
      awaitItem().shouldNotBeNull()
        .body
        .shouldBeInstanceOf<InAppBrowserModel>()
        .open()

      inAppBrowserNavigator.onOpenCalls.awaitItem()
        .shouldBe("https://bitkey.world/")

      // Simulate browser close
      inAppBrowserNavigator.onCloseCallback?.invoke()

      awaitItem().shouldBeNull()
    }
  }

  test("W3 upgrade blocker does not show for W3 accounts") {
    coachmarkService.defaultCoachmarks = listOf(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)

    val w3Props = InterstitialUiProps(
      account = FullAccountW3Mock,
      isComingFromOnboarding = false
    )

    stateMachine().test(props = w3Props) {
      awaitItem().shouldBeNull()
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
      awaitItem().shouldNotBeNull()
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

      awaitItem().shouldNotBeNull()
        .body
        .shouldBeInstanceOf<W3UpgradeBlockerBodyModel>()
        .onClose()

      coachmarkService.markDisplayedTurbine.awaitItem()
        .shouldBe(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)

      awaitItem().shouldBeNull()
    }
  }
})
