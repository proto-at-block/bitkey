package build.wallet.time

import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class DelayerDefaultTest : FunSpec({

  val delayer = Delayer.Default

  test("default delayer should use actual delay").config(coroutineTestScope = true) {

    // Naive test to check that the delay is actually happening
    testCoroutineScheduler.timeSource.measureTime { delayer.delay(50.milliseconds) }
      .shouldBeEqual(50.milliseconds)
  }

  context("withMinimumDelay") {
    test("return result when execution completed before minimum delay") {
      runTest {
        testTimeSource.measureTime {
          delayer.withMinimumDelay(minimumDelay = 2.seconds) {
            advanceTimeBy(1.seconds.inWholeMilliseconds)
          }
        }.shouldBe(2.seconds)
      }
    }

    test("return result without additional delay when execution completed after minimum delay") {
      runTest {
        testTimeSource.measureTime {
          delayer.withMinimumDelay(minimumDelay = 2.seconds) {
            advanceTimeBy(3.seconds.inWholeMilliseconds)
          }
        }.shouldBe(3.seconds)
      }
    }
  }
})
