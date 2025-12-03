package build.wallet.statemachine.recovery.losthardware

import app.cash.turbine.plusAssign
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.recovery.LostHardwareServerRecoveryMock
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.RotatedAuthKeys
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiProps
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiStateMachineImpl
import build.wallet.statemachine.root.RemainingRecoveryDelayWordsUpdateFrequency
import build.wallet.statemachine.ui.matchers.shouldHaveSubtitle
import build.wallet.statemachine.ui.matchers.shouldHaveTitle
import build.wallet.statemachine.ui.matchers.shouldNotHaveSubtitle
import build.wallet.statemachine.ui.robots.click
import build.wallet.time.ClockFake
import build.wallet.time.DurationFormatterFake
import build.wallet.time.someInstant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Instant
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class HardwareRecoveryStatusCardUiStateMachineImplTests : FunSpec({
  val clock = ClockFake()
  val recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create)
  val stateMachine = HardwareRecoveryStatusCardUiStateMachineImpl(
    clock = clock,
    durationFormatter = DurationFormatterFake(),
    recoveryStatusService = recoveryStatusService,
    remainingRecoveryDelayWordsUpdateFrequency = RemainingRecoveryDelayWordsUpdateFrequency(1.seconds)
  )

  val onClickCalls = turbines.create<Unit>("on click calls")

  val props = HardwareRecoveryStatusCardUiProps(
    account = FullAccountMock,
    onClick = {
      onClickCalls += Unit
    }
  )

  test("null when no recovery") {
    recoveryStatusService.reset()
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
    }
  }

  test("null when in completion phase (RotatedAuthKeys)") {
    recoveryStatusService.recoveryStatus.value = RotatedAuthKeys(
      fullAccountId = FullAccountMock.accountId,
      appSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.appKey,
      appGlobalAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey,
      appRecoveryAuthKey = FullAccountMock.keybox.activeAppKeyBundle.recoveryAuthKey,
      hardwareSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.hardwareKey,
      hardwareAuthKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
      appGlobalAuthKeyHwSignature = FullAccountMock.keybox.appGlobalAuthKeyHwSignature,
      factorToRecover = Hardware,
      sealedCsek = "sealed-csek".encodeUtf8(),
      sealedSsek = null
    )
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
    }
  }

  test("ready to complete when delay period is zero") {
    val initiatedRecovery = InitiatedRecovery(
      fullAccountId = FullAccountMock.accountId,
      appSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.appKey,
      appGlobalAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey,
      appRecoveryAuthKey = FullAccountMock.keybox.activeAppKeyBundle.recoveryAuthKey,
      hardwareSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.hardwareKey,
      hardwareAuthKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
      appGlobalAuthKeyHwSignature = FullAccountMock.keybox.appGlobalAuthKeyHwSignature,
      factorToRecover = Hardware,
      serverRecovery = LostHardwareServerRecoveryMock.copy(
        delayStartTime = clock.now(),
        delayEndTime = clock.now() // Delay period is complete
      )
    )
    recoveryStatusService.reset()
    recoveryStatusService.recoveryStatus.value = initiatedRecovery

    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<CardModel>()
        .shouldHaveTitle("Replacement Ready")
        .shouldNotHaveSubtitle()
        .click()
      onClickCalls.awaitItem()
    }
  }

  test("delay in progress") {
    val initiatedRecovery = InitiatedRecovery(
      fullAccountId = FullAccountMock.accountId,
      appSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.appKey,
      appGlobalAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey,
      appRecoveryAuthKey = FullAccountMock.keybox.activeAppKeyBundle.recoveryAuthKey,
      hardwareSpendingKey = FullAccountMock.keybox.activeSpendingKeyset.hardwareKey,
      hardwareAuthKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
      appGlobalAuthKeyHwSignature = FullAccountMock.keybox.appGlobalAuthKeyHwSignature,
      factorToRecover = Hardware,
      serverRecovery = LostHardwareServerRecoveryMock.copy(
        delayStartTime = Instant.DISTANT_PAST,
        delayEndTime = someInstant + 5.days
      )
    )
    recoveryStatusService.recoveryStatus.value = initiatedRecovery

    stateMachine.test(props) {
      awaitItem().shouldBeTypeOf<CardModel>()
        .shouldHaveTitle("Replacement pending...")
        .shouldHaveSubtitle("5d")
        .click()
      onClickCalls.awaitItem()
    }
  }
})
