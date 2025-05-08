package build.wallet.worker

import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.platform.app.AppSessionState
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AppWorkerExecutorImplTests : FunSpec({
  // TODO(W-10571): use real dispatcher.
  coroutineTestScope = true

  val appSessionManager = AppSessionManagerFake()

  fun TestScope.createExecutor(vararg workers: AppWorker): AppWorkerExecutorImpl {
    return AppWorkerExecutorImpl(
      appScope = backgroundScope,
      appSessionManager = appSessionManager,
      workerProvider = { setOf(*workers) }
    )
  }

  beforeTest {
    appSessionManager.reset()
  }

  test("executor executes all workers asynchronously") {
    val delayedWorker = AppWorkerFake(executionDelay = 2.seconds)
    val immediateWorker = AppWorkerFake()
    val executor = createExecutor(delayedWorker, immediateWorker)

    // execute all workers
    executor.executeAll()
    testCoroutineScheduler.advanceUntilIdle()

    // haven't advanced time yet, so the workers should not have executed
    delayedWorker.completions.shouldBeEqual(0)
    immediateWorker.completions.shouldBeEqual(0)

    // advance time enough only for the immediate worker to execute
    testCoroutineScheduler.advanceTimeBy(1.seconds)
    delayedWorker.completions.shouldBeEqual(0)
    immediateWorker.completions.shouldBeEqual(1)

    // advance time enough for delayed worker to execute as well
    testCoroutineScheduler.advanceTimeBy(2.seconds)
    delayedWorker.completions.shouldBeEqual(1)
    immediateWorker.completions.shouldBeEqual(1)

    // reset workers and execute again
    delayedWorker.reset()
    immediateWorker.reset()
    executor.executeAll()

    // advance time enough that both workers require to execute, but
    // since they have already executed once, they should not execute again
    testCoroutineScheduler.advanceTimeBy(3.seconds)
    delayedWorker.completions.shouldBeEqual(0)
    immediateWorker.completions.shouldBeEqual(0)
  }

  test("executor runs event workers on event emission") {
    val testFlow = MutableSharedFlow<Unit>()
    val eventWorker = AppWorkerFake(
      runStrategy = setOf(RunStrategy.OnEvent(observer = testFlow))
    )

    val executor = createExecutor(eventWorker)

    executor.executeAll()
    testCoroutineScheduler.runCurrent()

    // No events emitted yet
    eventWorker.completions.shouldBe(0)

    // Emit an event
    testFlow.emit(Unit)
    testCoroutineScheduler.runCurrent()
    eventWorker.completions.shouldBe(1)
  }

  test("executor repeats periodic workers") {
    val periodicWorker = AppWorkerFake(
      runStrategy = setOf(RunStrategy.Periodic(interval = 1.minutes))
    )
    val multiStrategy = AppWorkerFake(
      runStrategy = setOf(
        RunStrategy.Startup(),
        RunStrategy.Periodic(interval = 1.minutes)
      )
    )
    val executor = createExecutor(periodicWorker, multiStrategy)

    // execute all workers
    executor.executeAll()
    testCoroutineScheduler.advanceUntilIdle()

    // haven't advanced time yet, so the workers should not have executed
    periodicWorker.completions.shouldBeEqual(0)
    multiStrategy.completions.shouldBeEqual(0)

    // advance time enough for the multi worker to execute
    testCoroutineScheduler.advanceTimeBy(1.seconds)
    periodicWorker.completions.shouldBeEqual(0)
    multiStrategy.completions.shouldBeEqual(1)

    // advance time enough for the periodic worker to execute
    testCoroutineScheduler.advanceTimeBy(1.minutes)
    periodicWorker.completions.shouldBeEqual(1)
    multiStrategy.completions.shouldBeEqual(2)

    // advance time to repeat the periodic workers
    testCoroutineScheduler.advanceTimeBy(1.minutes)
    periodicWorker.completions.shouldBeEqual(2)
    multiStrategy.completions.shouldBeEqual(3)
  }

  test("executor manually runs refresh workers") {
    val refreshOperation = RefreshOperation("Fake")

    val refreshWorker = AppWorkerFake(
      runStrategy = setOf(RunStrategy.Refresh(RefreshOperationFilter.Subset(refreshOperation)))
    )
    val multiStrategy = AppWorkerFake(
      runStrategy = setOf(
        RunStrategy.Refresh(RefreshOperationFilter.Any),
        RunStrategy.Periodic(interval = 1.minutes)
      )
    )
    val executor = createExecutor(refreshWorker, multiStrategy)

    // execute all workers
    executor.executeAll()

    // advance time enough for the multi worker to execute
    testCoroutineScheduler.advanceTimeBy(1.minutes)
    testCoroutineScheduler.runCurrent()
    refreshWorker.completions.shouldBeEqual(0)
    multiStrategy.completions.shouldBeEqual(1)

    // manually refresh workers
    executor.runRefreshOperation(refreshOperation)
    testCoroutineScheduler.runCurrent()
    refreshWorker.completions.shouldBeEqual(1)
    multiStrategy.completions.shouldBeEqual(2)
  }

  test("executor times-out") {
    val worker = AppWorkerFake(
      timeout = TimeoutStrategy.Always(10.seconds),
      executionDelay = 20.seconds
    )

    val executor = createExecutor(worker)

    executor.executeAll()
    testCoroutineScheduler.advanceTimeBy(5.seconds)
    testCoroutineScheduler.runCurrent()

    worker.completions.shouldBeEqual(0)
    worker.cancellations.shouldBeEqual(0)

    testCoroutineScheduler.advanceTimeBy(10.seconds)
    testCoroutineScheduler.runCurrent()
    worker.completions.shouldBeEqual(0)
    worker.cancellations.shouldBeEqual(1)

    testCoroutineScheduler.advanceTimeBy(1.minutes)
    testCoroutineScheduler.runCurrent()
    worker.completions.shouldBeEqual(0)
    worker.cancellations.shouldBeEqual(1)
  }

  test("executor times-out for refresh-only") {
    val refreshOperation = RefreshOperation("Fake")
    val worker = AppWorkerFake(
      runStrategy = setOf(
        RunStrategy.Startup(),
        RunStrategy.Refresh(RefreshOperationFilter.Any)
      ),
      timeout = TimeoutStrategy.RefreshOnly(10.seconds),
      executionDelay = 20.seconds
    )

    val executor = createExecutor(worker)

    executor.executeAll()
    testCoroutineScheduler.advanceTimeBy(5.seconds)
    testCoroutineScheduler.runCurrent()

    worker.completions.shouldBeEqual(0)
    worker.cancellations.shouldBeEqual(0)

    // Worker does not time-out for startup execution:
    testCoroutineScheduler.advanceTimeBy(20.seconds)
    testCoroutineScheduler.runCurrent()
    worker.completions.shouldBeEqual(1)
    worker.cancellations.shouldBeEqual(0)

    worker.reset()

    // Worker times out for refresh execution:
    executor.runRefreshOperation(refreshOperation)
    testCoroutineScheduler.advanceTimeBy(20.seconds)
    testCoroutineScheduler.runCurrent()
    worker.completions.shouldBeEqual(0)
    worker.cancellations.shouldBeEqual(1)
  }

  test("executor retries workers") {
    val worker = AppWorkerFake(
      retryStrategy = RetryStrategy.Always(
        delay = 10.seconds,
        retries = 2
      ),
      timeout = TimeoutStrategy.Always(1.seconds),
      executionDelay = 1.minutes
    )

    val executor = createExecutor(worker)

    executor.executeAll()
    testCoroutineScheduler.runCurrent()

    worker.completions.shouldBeEqual(0)
    worker.cancellations.shouldBeEqual(0)
    worker.attempts.shouldBeEqual(1)

    // Advance clock past timeout, but before retry
    testCoroutineScheduler.advanceTimeBy(1.seconds)
    testCoroutineScheduler.runCurrent()

    // Worker should not be retried until delay passes
    worker.completions.shouldBeEqual(0)
    worker.cancellations.shouldBeEqual(1)
    worker.attempts.shouldBeEqual(1)

    // Advance clock past delay
    testCoroutineScheduler.advanceTimeBy(10.seconds)
    testCoroutineScheduler.runCurrent()

    // Worker should be retried
    worker.completions.shouldBeEqual(0)
    worker.cancellations.shouldBeEqual(1)
    worker.attempts.shouldBeEqual(2)

    // Advance clock far in the future
    testCoroutineScheduler.advanceTimeBy(Duration.INFINITE)
    testCoroutineScheduler.runCurrent()

    // Worker should be retried until retries are exhausted
    worker.completions.shouldBeEqual(0)
    worker.cancellations.shouldBeEqual(3)
    worker.attempts.shouldBeEqual(3)
  }

  test("executor skips workers in background") {
    appSessionManager.appSessionState.value = AppSessionState.BACKGROUND
    val eventFlow = MutableSharedFlow<Unit>()
    val worker = AppWorkerFake(
      runStrategy = setOf(
        RunStrategy.OnEvent(
          observer = eventFlow,
          backgroundStrategy = BackgroundStrategy.Skip
        )
      )
    )

    val executor = createExecutor(worker)

    executor.executeAll()
    testCoroutineScheduler.runCurrent()
    worker.attempts.shouldBeEqual(0)

    // Event does not trigger a run
    eventFlow.emit(Unit)
    testCoroutineScheduler.runCurrent()

    worker.attempts.shouldBeEqual(0)

    // Changing app to foreground does not resume the worker event:
    appSessionManager.appSessionState.value = AppSessionState.FOREGROUND
    testCoroutineScheduler.runCurrent()

    worker.attempts.shouldBeEqual(0)
  }

  test("executor suspends workers until app re-enters foreground") {
    appSessionManager.appSessionState.value = AppSessionState.BACKGROUND
    val eventFlow = MutableSharedFlow<Unit>()
    val worker = AppWorkerFake(
      runStrategy = setOf(
        RunStrategy.OnEvent(
          observer = eventFlow,
          backgroundStrategy = BackgroundStrategy.Wait
        )
      )
    )

    val executor = createExecutor(worker)

    executor.executeAll()
    testCoroutineScheduler.runCurrent()
    worker.attempts.shouldBeEqual(0)

    // Event does not trigger a run
    eventFlow.emit(Unit)
    testCoroutineScheduler.runCurrent()

    worker.attempts.shouldBeEqual(0)

    // Changing app to foreground resumes the worker event:
    appSessionManager.appSessionState.value = AppSessionState.FOREGROUND
    testCoroutineScheduler.runCurrent()

    worker.attempts.shouldBeEqual(1)
  }

  test("executor allows background workers when flagged") {
    appSessionManager.appSessionState.value = AppSessionState.BACKGROUND
    val eventFlow = MutableSharedFlow<Unit>()
    val worker = AppWorkerFake(
      runStrategy = setOf(
        RunStrategy.OnEvent(
          observer = eventFlow,
          backgroundStrategy = BackgroundStrategy.Allowed
        )
      )
    )

    val executor = createExecutor(worker)

    executor.executeAll()
    testCoroutineScheduler.runCurrent()
    worker.attempts.shouldBeEqual(0)

    // Event triggers a run, because this worker is allowed to run in the background
    eventFlow.emit(Unit)
    testCoroutineScheduler.runCurrent()

    worker.attempts.shouldBeEqual(1)
  }
})
