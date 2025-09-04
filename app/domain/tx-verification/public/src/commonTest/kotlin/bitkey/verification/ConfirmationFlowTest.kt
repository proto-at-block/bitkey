package bitkey.verification

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmationFlowTest : FunSpec({

  test("Confirmation poll emits latest state") {
    runTest {
      var state: ConfirmationState<String> = ConfirmationState.Pending
      val flow = pollForConfirmation { state }

      flow.test {
        awaitItem().shouldBe(ConfirmationState.Pending)
        advanceTimeBy(5.seconds)
        awaitItem().shouldBe(ConfirmationState.Pending)
        state = ConfirmationState.Confirmed("Success")
        advanceTimeBy(5.seconds)
        awaitItem().shouldBe(ConfirmationState.Confirmed("Success"))
        awaitComplete()
      }
    }
  }

  test("Confirmation poll rejected") {
    runTest {
      var state: ConfirmationState<String> = ConfirmationState.Pending
      val flow = pollForConfirmation { state }

      flow.test {
        awaitItem().shouldBe(ConfirmationState.Pending)
        state = ConfirmationState.Rejected
        advanceTimeBy(5.seconds)
        awaitItem().shouldBe(ConfirmationState.Rejected)
        awaitComplete()
      }
    }
  }

  test("Confirmation poll expired") {
    runTest {
      var state: ConfirmationState<String> = ConfirmationState.Pending
      val flow = pollForConfirmation { state }

      flow.test {
        awaitItem().shouldBe(ConfirmationState.Pending)
        state = ConfirmationState.Expired
        advanceTimeBy(5.seconds)
        awaitItem().shouldBe(ConfirmationState.Expired)
        awaitComplete()
      }
    }
  }

  test("Confirmation poll handles and recovers from unexpected errors") {
    runTest {
      var state: ConfirmationState<String>? = ConfirmationState.Pending
      val flow = pollForConfirmation {
        state ?: throw IllegalStateException("Unexpected error")
      }

      flow.test {
        awaitItem().shouldBe(ConfirmationState.Pending)
        state = null // trigger error
        advanceTimeBy(10.seconds)
        expectNoEvents()
        state = ConfirmationState.Confirmed("Success") // recover from error
        advanceTimeBy(5.seconds)
        awaitItem().shouldBe(ConfirmationState.Confirmed("Success"))
        awaitComplete()
      }
    }
  }

  test("Confirmation poll calls onCancel when flow is canceled") {
    runTest {
      var state: ConfirmationState<String> = ConfirmationState.Pending
      var onCancelCalled = false

      val flow = pollForConfirmation(
        onCancel = {
          onCancelCalled = true
        }
      ) { state }

      flow.test {
        awaitItem().shouldBe(ConfirmationState.Pending)
        advanceTimeBy(5.seconds)
        awaitItem().shouldBe(ConfirmationState.Pending)

        cancelAndIgnoreRemainingEvents()

        onCancelCalled.shouldBe(true)
      }
    }
  }
})
