package build.wallet.testing

import build.wallet.logging.SensitiveDataResult
import build.wallet.logging.SensitiveDataValidator
import io.kotest.assertions.assertSoftly
import io.kotest.core.listeners.ProjectListener
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Validates that no sensitive data (best effort estimation) has been logged during tests.
 */
internal class SensitiveDataLogListener : ProjectListener {
  override suspend fun afterProject() {
    with(TestLogStoreWriter) {
      shouldNotContainSensitiveData()
      clear()
    }
  }

  private fun TestLogStoreWriter.shouldNotContainSensitiveData() {
    assertSoftly {
      logs.forEach { entry ->
        SensitiveDataValidator.check(entry).shouldBeInstanceOf<SensitiveDataResult.NoneFound>()
      }
    }
  }
}
