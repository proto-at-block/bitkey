package build.wallet.testing.extensions

import build.wallet.logging.log
import build.wallet.testing.tags.TestTag.FlakyTest
import io.kotest.assertions.retry
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import kotlin.time.Duration

/**
 * Custom test wrapper that will add a retry loop around any tests that have the `FlakyTest` tag.
 */
class RetryFlakyTestsExtension(
  private val attempts: Int,
  private val timeout: Duration,
  private val delay: Duration,
) : TestCaseExtension {
  override suspend fun intercept(
    testCase: TestCase,
    execute: suspend (TestCase) -> TestResult,
  ): TestResult {
    return if (testCase.config.tags.contains(FlakyTest)) {
      log { "Running flaky test '${testCase.name.testName}' with retry logic" }
      retry(attempts, timeout, delay) {
        val result = execute(testCase)
        if (result.isErrorOrFailure) {
          result.errorOrNull?.let { throw it }
          throw AssertionError("Unknown test failure '${testCase.name.testName}', retrying...")
        }
        result
      }
    } else {
      execute(testCase)
    }
  }
}
