package build.wallet.time

import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@OptIn(ExperimentalStdlibApi::class, ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class DelayerDefaultTest : FunSpec({

  test("default delayer should use actual delay").config(coroutineTestScope = true) {
    val delayer = Delayer.Default

    // Naive test to check that the delay is actually happening
    testCoroutineScheduler.timeSource.measureTime { delayer.delay(50.milliseconds) }
      .shouldBeEqual(50.milliseconds)
  }
})
