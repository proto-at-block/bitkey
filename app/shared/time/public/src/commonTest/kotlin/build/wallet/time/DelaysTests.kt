@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.time

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class DelaysTests : FunSpec({
  context("withMinimumDelay") {
    test("return result when execution completed before minimum delay") {
      measureTime {
        withMinimumDelay(minimumDelay = 10.milliseconds) {
          delay(2.milliseconds)
        }
      }.shouldBeGreaterThanOrEqualTo(10.milliseconds)
    }

    test("return result without additional delay when execution completed after minimum delay") {
      measureTime {
        withMinimumDelay(minimumDelay = 2.milliseconds) {
          delay(10.milliseconds)
        }
      }.shouldBeGreaterThanOrEqualTo(10.milliseconds)
    }
  }
})
