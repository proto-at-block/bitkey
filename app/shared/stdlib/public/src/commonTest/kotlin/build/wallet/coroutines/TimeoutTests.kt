package build.wallet.coroutines

import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TimeoutTests : FunSpec({
  test("withTimeoutThrowing - ok") {
    runBlocking {
      launch {
        withTimeoutThrowing(1.seconds) {
          // noop
        }
      }.join()
    }
  }

  test("withTimeoutThrowing - throw") {
    shouldThrow<TimeoutException> {
      // Use `runBlocking` to force exception to be thrown in the test thread.
      @Suppress("ForbiddenMethodCall")
      runBlocking {
        // regular `withTimeout` throws `TimeoutCancellationException` which implements `CancellationException`,
        // telling `launch` to simply cancel itself, instead of throwing timeout exception.
        // Using regular `withTimeout` here will not throw.
        // See https://github.com/Kotlin/kotlinx.coroutines/issues/1374
        launch {
          withTimeoutThrowing(1.milliseconds) {
            delay(1.seconds)
          }
        }.join()
      }
    }
  }

  test("withTimeoutResult - ok") {
    withTimeoutResult(1.milliseconds) {
      // noop
    }.shouldBeOk()
  }

  test("withTimeoutResult - error") {
    withTimeoutResult(1.milliseconds) {
      delay(10.milliseconds)
    }.shouldBeErrOfType<TimeoutException>()
  }
})
