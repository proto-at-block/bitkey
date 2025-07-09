package bitkey.ui.statemachine.interstitial

import bitkey.recovery.fundslost.AtRiskCause
import bitkey.recovery.fundslost.FundsLostRiskLevel
import bitkey.recovery.fundslost.FundsLostRiskServiceFake
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.inheritance.InheritanceUpsellServiceFake
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.recovery.socrec.PostSocRecTaskRepositoryMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataStateMachine
import build.wallet.statemachine.inheritance.InheritanceUpsellBodyModel
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryProps as LostHardwareRecoveryDataProps

class InterstitialUiStateMachineImplTests : FunSpec({
  val awaitingNewHardwareData = AwaitingNewHardwareData(
    newAppGlobalAuthKey = AppGlobalAuthPublicKeyMock,
    addHardwareKeys = { _, _ -> }
  )

  val lostHardwareRecoveryDataStateMachine = object : LostHardwareRecoveryDataStateMachine,
    StateMachineMock<LostHardwareRecoveryDataProps, LostHardwareRecoveryData>(
      awaitingNewHardwareData
    ) {}

  val lostHardwareUiStateMachine = object : LostHardwareRecoveryUiStateMachine,
    ScreenStateMachineMock<LostHardwareRecoveryProps>(id = "someone-else-recovering") {}

  val fundsLostRiskService = FundsLostRiskServiceFake()
  val inheritanceUpsellService = InheritanceUpsellServiceFake()

  val stateMachine = InterstitialUiStateMachineImpl(
    lostHardwareRecoveryDataStateMachine = lostHardwareRecoveryDataStateMachine,
    lostHardwareUiStateMachine = lostHardwareUiStateMachine,
    recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create),
    fundsLostRiskService = fundsLostRiskService,
    inheritanceUpsellService = inheritanceUpsellService,
    appSessionManager = AppSessionManagerFake(),
    recoveryIncompleteRepository = PostSocRecTaskRepositoryMock()
  )

  val props = InterstitialUiProps(
    account = FullAccountMock,
    isComingFromOnboarding = false
  )

  beforeTest {
    inheritanceUpsellService.reset()
    fundsLostRiskService.reset()
    lostHardwareRecoveryDataStateMachine.reset()
  }

  test("default screen model is null") {
    stateMachine.test(props = props) {
      awaitItem().shouldBeNull()
    }
  }

  test("when at risk, show risk screen model") {
    fundsLostRiskService.riskLevel.value = FundsLostRiskLevel.AtRisk(cause = AtRiskCause.MissingHardware)
    inheritanceUpsellService.markUpsellAsSeen()

    stateMachine.test(props = props) {
      awaitItem().shouldNotBeNull()
        .body
        .shouldBeInstanceOf<WalletAtRiskInterstitialBodyModel>()
        .onClose()

      awaitItem().shouldBeNull()
    }
  }

  test("inheritance upsell is shown when applicable") {
    stateMachine.test(props = props) {
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

  test("when at risk, don't show risk screen model if coming from onboarding") {
    fundsLostRiskService.riskLevel.value = FundsLostRiskLevel.AtRisk(cause = AtRiskCause.MissingHardware)

    stateMachine.test(props = props.copy(isComingFromOnboarding = true)) {
      awaitItem().shouldBeNull()
    }
  }
})
