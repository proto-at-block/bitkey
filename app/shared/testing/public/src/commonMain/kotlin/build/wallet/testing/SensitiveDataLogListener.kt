package build.wallet.testing

import io.kotest.assertions.assertSoftly
import io.kotest.core.listeners.ProjectListener
import io.kotest.matchers.string.shouldNotContain
import kotlin.text.RegexOption.IGNORE_CASE

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
        sensitiveDataIndicators.forEach { indicator ->
          entry.tag.shouldNotContain(indicator)
          entry.message.shouldNotContain(indicator)
        }
      }
    }
  }

  // Naive way of catching logs that might contain sensitive data.
  private val sensitiveDataIndicators =
    listOf(
      Regex("\\b[tx]prv\\w{78,112}", IGNORE_CASE)
    )
}
