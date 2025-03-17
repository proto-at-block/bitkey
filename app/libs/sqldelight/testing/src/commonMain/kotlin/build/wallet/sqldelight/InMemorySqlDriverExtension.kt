package build.wallet.sqldelight

import io.kotest.core.TestConfiguration
import io.kotest.core.extensions.Extension
import io.kotest.core.listeners.AfterTestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult

/**
 * Kotest extension that prints to console contents of a database tables created by
 * [InMemorySqlDriverFactory].
 *
 * Use this extension via [inMemorySqlDriver].
 */
class InMemorySqlDriverExtension(
  val factory: InMemorySqlDriverFactory,
) : Extension, AfterTestListener {
  override suspend fun afterTest(
    testCase: TestCase,
    result: TestResult,
  ) {
    if (result.isErrorOrFailure) {
      try {
        println("===================")
        println("Database contents:")
        factory.sqlDriver?.let {
          println(it.databaseContents().renderText())
        } ?: run {
          println("SqlDriver was not initialized.")
        }
        println("===================")
      } catch (e: Exception) {
        println("Failed to print database contents:")
        println(e.stackTraceToString())
      }
    }
  }
}

/** Registers [InMemorySqlDriverExtension]. */
fun TestConfiguration.inMemorySqlDriver(): InMemorySqlDriverExtension {
  return InMemorySqlDriverExtension(InMemorySqlDriverFactory()).also {
    extension(it)
  }
}
