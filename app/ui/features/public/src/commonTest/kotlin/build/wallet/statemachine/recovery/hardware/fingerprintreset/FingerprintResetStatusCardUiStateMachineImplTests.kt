package build.wallet.statemachine.recovery.hardware.fingerprintreset

import app.cash.turbine.plusAssign
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionType
import bitkey.privilegedactions.FingerprintResetF8eClientFake
import bitkey.privilegedactions.FingerprintResetServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.awaitUntilNotNull
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.root.RemainingRecoveryDelayWordsUpdateFrequency
import build.wallet.statemachine.ui.matchers.shouldHaveSubtitle
import build.wallet.statemachine.ui.matchers.shouldHaveTitle
import build.wallet.statemachine.ui.robots.click
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FingerprintResetStatusCardUiStateMachineImplTests : FunSpec({
  val clock = ClockFake()
  val fingerprintResetF8eClient = FingerprintResetF8eClientFake(
    turbine = turbines::create,
    clock = clock
  )
  val accountService = AccountServiceFake()
  val fingerprintResetService = FingerprintResetServiceFake(
    privilegedActionF8eClient = fingerprintResetF8eClient,
    accountService = accountService,
    clock = clock
  )
  val stateMachine = FingerprintResetStatusCardUiStateMachineImpl(
    clock = clock,
    fingerprintResetService = fingerprintResetService,
    remainingRecoveryDelayWordsUpdateFrequency = RemainingRecoveryDelayWordsUpdateFrequency(1.seconds)
  )

  val onClickCalls = turbines.create<String>("on click calls")

  val props = FingerprintResetStatusCardUiProps(
    account = FullAccountMock,
    onClick = { actionId ->
      onClickCalls += actionId
    }
  )

  beforeTest {
    clock.reset()
    fingerprintResetService.reset()
  }

  test("returns null when no fingerprint reset action is pending") {
    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(null)

    stateMachine.test(props) {
      awaitItem().shouldBeNull()
    }
  }

  test("returns null when fingerprint reset is ready to complete") {

    val completedAction = PrivilegedActionInstance(
      id = "test-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now - 7.days,
        delayEndTime = clock.now - 1.hours, // Already completed
        cancellationToken = "cancel-token",
        completionToken = "complete-token"
      )
    )

    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(completedAction)

    stateMachine.test(props) {
      awaitItem().shouldBeNull()
    }
  }

  test("returns card model when fingerprint reset is in progress with days remaining") {
    val activeAction = PrivilegedActionInstance(
      id = "test-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now,
        delayEndTime = clock.now + 3.days + 2.hours, // 3 days, 2 hours remaining
        cancellationToken = "cancel-token",
        completionToken = "complete-token"
      )
    )

    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(activeAction)

    stateMachine.test(props) {
      awaitUntilNotNull().shouldBeTypeOf<CardModel>().apply {
        shouldHaveTitle("Fingerprint reset in progress")
        shouldHaveSubtitle("3 days remaining...")
        click()
      }

      onClickCalls.awaitItem().shouldBeTypeOf<String>()
    }
  }

  test("returns card model when fingerprint reset is in progress with hours remaining") {
    val activeAction = PrivilegedActionInstance(
      id = "test-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now,
        delayEndTime = clock.now + 5.hours + 30.minutes, // 5 hours, 30 minutes remaining
        cancellationToken = "cancel-token",
        completionToken = "complete-token"
      )
    )

    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(activeAction)

    stateMachine.test(props) {
      awaitUntilNotNull().shouldBeTypeOf<CardModel>().apply {
        shouldHaveTitle("Fingerprint reset in progress")
        shouldHaveSubtitle("5 hours remaining...")
      }
    }
  }

  test("returns card model when fingerprint reset is in progress with minutes remaining") {
    val activeAction = PrivilegedActionInstance(
      id = "test-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now,
        delayEndTime = clock.now + 45.minutes + 30.seconds, // 45 minutes remaining
        cancellationToken = "cancel-token",
        completionToken = "complete-token"
      )
    )

    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(activeAction)

    stateMachine.test(props) {
      awaitUntilNotNull().shouldBeTypeOf<CardModel>().apply {
        shouldHaveTitle("Fingerprint reset in progress")
        shouldHaveSubtitle("45 minutes remaining...")
      }
    }
  }

  test("returns card model with 'Less than 1 minute remaining' for very short durations") {
    val activeAction = PrivilegedActionInstance(
      id = "test-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now,
        delayEndTime = clock.now + 30.seconds, // 30 seconds remaining
        cancellationToken = "cancel-token",
        completionToken = "complete-token"
      )
    )

    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(activeAction)

    stateMachine.test(props) {
      awaitUntilNotNull().shouldBeTypeOf<CardModel>().apply {
        shouldHaveTitle("Fingerprint reset in progress")
        shouldHaveSubtitle("Less than 1 minute remaining...")
      }
    }
  }

  test("returns card model with singular forms when exactly 1 day remaining") {
    val activeAction = PrivilegedActionInstance(
      id = "test-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now,
        delayEndTime = clock.now + 1.days + 30.minutes, // 1 day remaining
        cancellationToken = "cancel-token",
        completionToken = "complete-token"
      )
    )

    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(activeAction)

    stateMachine.test(props) {
      awaitUntilNotNull().shouldBeTypeOf<CardModel>().apply {
        shouldHaveTitle("Fingerprint reset in progress")
        shouldHaveSubtitle("1 day remaining...")
      }
    }
  }

  test("returns card model with singular forms when exactly 1 hour remaining") {
    val activeAction = PrivilegedActionInstance(
      id = "test-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now,
        delayEndTime = clock.now + 1.hours + 30.seconds, // 1 hour remaining
        cancellationToken = "cancel-token",
        completionToken = "complete-token"
      )
    )

    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(activeAction)

    stateMachine.test(props) {
      awaitUntilNotNull().shouldBeTypeOf<CardModel>().apply {
        shouldHaveTitle("Fingerprint reset in progress")
        shouldHaveSubtitle("1 hour remaining...")
      }
    }
  }

  test("returns card model with singular forms when exactly 1 minute remaining") {
    val activeAction = PrivilegedActionInstance(
      id = "test-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now,
        delayEndTime = clock.now + 1.minutes + 10.seconds, // 1 minute remaining
        cancellationToken = "cancel-token",
        completionToken = "complete-token"
      )
    )

    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(activeAction)

    stateMachine.test(props) {
      awaitUntilNotNull().shouldBeTypeOf<CardModel>().apply {
        shouldHaveTitle("Fingerprint reset in progress")
        shouldHaveSubtitle("1 minute remaining...")
      }
    }
  }

  test("card automatically disappears when delay period completes") {
    val activeAction = PrivilegedActionInstance(
      id = "test-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now,
        delayEndTime = clock.now + 100.milliseconds, // Very short delay
        cancellationToken = "cancel-token",
        completionToken = "complete-token"
      )
    )

    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(activeAction)

    stateMachine.test(props) {
      // Initially should show the card
      awaitUntilNotNull().shouldBeTypeOf<CardModel>().apply {
        shouldHaveTitle("Fingerprint reset in progress")
        shouldHaveSubtitle("Less than 1 minute remaining...")
      }

      // Advance time past the completion
      clock.advanceBy(200.milliseconds)

      // Card should disappear
      awaitItem().shouldBeNull()
    }
  }

  test("onClick callback is invoked with correct action ID") {
    val actionId = "test-action-123"
    val activeAction = PrivilegedActionInstance(
      id = actionId,
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now,
        delayEndTime = clock.now + 1.hours,
        cancellationToken = "cancel-token",
        completionToken = "complete-token"
      )
    )

    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(activeAction)

    stateMachine.test(props) {
      awaitUntilNotNull().shouldBeTypeOf<CardModel>().apply {
        click()
      }

      onClickCalls.awaitItem().shouldBe(actionId)
    }
  }
})
