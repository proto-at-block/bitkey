package build.wallet.coroutines.util

import build.wallet.coroutines.advanceTimeBy
import build.wallet.coroutines.callCoroutineEvery
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class CallCoroutineEveryTests : FunSpec({
  test("calls block periodically") {
    runTest {
      var called = false

      backgroundScope.launch {
        callCoroutineEvery(frequency = 1.minutes) {
          called = true
        }
      }

      // First iteration calls coro
      runCurrent()
      called.shouldBeTrue()

      called = false

      advanceTimeBy(1.minutes - 1.milliseconds)
      called.shouldBeFalse()

      advanceTimeBy(1.milliseconds)
      called.shouldBeTrue()
    }
  }
})
