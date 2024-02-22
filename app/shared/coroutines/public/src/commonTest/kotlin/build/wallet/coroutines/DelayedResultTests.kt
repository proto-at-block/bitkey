@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package build.wallet.coroutines

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class DelayedResultTests : FunSpec({
  test("delay result return when execution completed before minimum delay") {
    runTest {
      testTimeSource.measureTime {
        delayedResult(minimumDuration = 2.seconds) {
          advanceTimeBy(1.seconds.inWholeMilliseconds)
        }
      }.shouldBe(2.seconds)
    }
  }

  test("return result without additional delay when execution completed after minimum delay") {
    runTest {
      testTimeSource.measureTime {
        delayedResult(minimumDuration = 2.seconds) {
          advanceTimeBy(3.seconds.inWholeMilliseconds)
        }
      }.shouldBe(3.seconds)
    }
  }
})
