@file:OptIn(ExperimentalUuidApi::class)

package build.wallet.coroutines

import io.kotest.core.test.TestScope
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Creates a new "background" [CoroutineScope] tied to the test's lifecycle.
 * The scope uses [Dispatchers.Unconfined] dispatcher.
 *
 * The primary purpose of this scope is to execute some "background" work (for example to launch
 * a concurrent worker) without blocking the test itself. When the test finishes (successfully or not),
 * the scope will be automatically cancelled.
 *
 * This is mean to be direct replacement for kotlin's [kotlinx.coroutines.test.TestScope] since
 * its APIs are effectively "deprecated": https://github.com/Kotlin/kotlinx.coroutines/issues/3919.
 */
fun TestScope.createBackgroundScope(
  context: CoroutineContext = EmptyCoroutineContext,
): CoroutineScope {
  val job = Job()
  val backgroundScope =
    CoroutineScope(job + Dispatchers.Unconfined + CoroutineName("BackgroundScope-${Uuid.random()}") + context)

  // We cancel the scope after each invocation, as it is common to validate test flakes
  // leveraging kotest's test case invocation config.
  testCase.spec.afterInvocation { _, _ ->
    backgroundScope.cancel()
    job.cancelAndJoin()
  }
  return backgroundScope
}
