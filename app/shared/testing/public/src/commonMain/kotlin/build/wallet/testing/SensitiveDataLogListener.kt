package build.wallet.testing

import build.wallet.logging.SensitiveDataValidator
import io.kotest.assertions.assertSoftly
import io.kotest.core.listeners.ProjectListener
import io.kotest.matchers.booleans.shouldBeFalse

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
        SensitiveDataValidator.isSensitiveData(entry).shouldBeFalse()
      }
    }
  }
}
