package build.wallet.functional.statemachine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.cash.turbine.turbineScope
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testIn
import build.wallet.withRealTimeout
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds

class DualStateMachineTests : FunSpec({
  data class CountingData(
    val count: Int,
    val inc: () -> Unit,
  )

  class CountingStateMachine : StateMachine<Unit, CountingData> {
    @Composable
    override fun model(props: Unit): CountingData {
      var count by remember { mutableStateOf(0) }

      return CountingData(count) { count++ }
    }
  }

  test("sanity check the counting state machine works") {
    val foo = CountingStateMachine()

    foo.test(props = Unit, useVirtualTime = true) {
      awaitItem().apply {
        count.shouldBe(0)
        inc()
      }
      awaitItem().apply {
        count.shouldBe(1)
        inc()
      }
      awaitItem().count.shouldBe(2)
    }
  }

  test("two state machines can both progress in virtual time") {
    runTest {
      turbineScope {
        val foo = CountingStateMachine()
        val bar = CountingStateMachine()

        val fooTester = foo.testIn(Unit, this)
        val barTester = bar.testIn(Unit, this)

        fooTester.awaitItem().apply {
          count.shouldBe(0)
          inc()
        }
        val fooData = fooTester.awaitItem()
        fooData.count.shouldBe(1)

        val barData =
          barTester.awaitItem().apply {
            count.shouldBe(0)
          }
        fooData.inc()
        barData.inc()

        fooTester.awaitItem().count.shouldBe(2)
        barTester.awaitItem().count.shouldBe(1)

        fooTester.cancelAndIgnoreRemainingEvents()
        barTester.cancelAndIgnoreRemainingEvents()
      }
    }
  }

  test("two state machines can both progress in real time") {
    withRealTimeout(3.seconds) {
      turbineScope {
        val foo = CountingStateMachine()
        val bar = CountingStateMachine()

        val fooTester = foo.testIn(Unit, this)
        val barTester = bar.testIn(Unit, this)

        fooTester.awaitItem().apply {
          count.shouldBe(0)
          inc()
        }
        val fooData = fooTester.awaitItem()
        fooData.count.shouldBe(1)

        val barData =
          barTester.awaitItem().apply {
            count.shouldBe(0)
          }
        fooData.inc()
        barData.inc()

        fooTester.awaitItem().count.shouldBe(2)
        barTester.awaitItem().count.shouldBe(1)

        fooTester.cancelAndIgnoreRemainingEvents()
        barTester.cancelAndIgnoreRemainingEvents()
      }
    }
  }
})
