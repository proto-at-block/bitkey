package build.wallet.gradle.logic.gradle

import org.gradle.api.logging.LogLevel.DEBUG
import org.gradle.api.logging.LogLevel.INFO
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent

internal fun Test.configureJvmTestLogging() {
  val projectLogLevel = project.logging.level // Access the current log level of the project

  val logEvents =
    mutableListOf(
      TestLogEvent.FAILED
    )

  // Log outputs when `--debug` or `--info` Gradle log level is used.
  if (projectLogLevel == DEBUG || projectLogLevel == INFO) {
    logEvents += TestLogEvent.STANDARD_OUT
    logEvents += TestLogEvent.STANDARD_ERROR
  }

  testLogging {
    info {
      events.addAll(logEvents)
    }
    events.addAll(logEvents)
    exceptionFormat = FULL
    showExceptions = true
    showCauses = true
    showStackTraces = true
  }
}
