package build.wallet.worker

import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration.Companion.seconds

class AppWorkerExecutorImplTests : FunSpec({
  // TODO(W-10571): use real dispatcher.
  coroutineTestScope = true

  val delayedWorker = AppWorkerFake(executionDelay = 2.seconds)
  val immediateWorker = AppWorkerFake()

  fun executor(appScope: CoroutineScope) =
    AppWorkerExecutorImpl(appScope, workerProvider = { setOf(delayedWorker, immediateWorker) })

  beforeTest {
    delayedWorker.reset()
    immediateWorker.reset()
  }

  test("executor executes all workers asynchronously") {
    val executor = executor(backgroundScope)

    // execute all workers
    executor.executeAll()
    testCoroutineScheduler.advanceUntilIdle()

    // haven't advanced time yet, so the workers should not have executed
    delayedWorker.executed.shouldBeFalse()
    immediateWorker.executed.shouldBeFalse()

    // advance time enough only for the immediate worker to execute
    testCoroutineScheduler.advanceTimeBy(1.seconds)
    delayedWorker.executed.shouldBeFalse()
    immediateWorker.executed.shouldBeTrue()

    // advance time enough for delayed worker to execute as well
    testCoroutineScheduler.advanceTimeBy(2.seconds)
    delayedWorker.executed.shouldBeTrue()
    immediateWorker.executed.shouldBeTrue()

    // reset workers and execute again
    delayedWorker.reset()
    immediateWorker.reset()
    executor.executeAll()

    // advance time enough that both workers require to execute, but
    // since they have already executed once, they should not execute again
    testCoroutineScheduler.advanceTimeBy(3.seconds)
    delayedWorker.executed.shouldBeFalse()
    immediateWorker.executed.shouldBeFalse()
  }
})
