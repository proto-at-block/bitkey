package build.wallet.logging

import build.wallet.logging.LogLevel.Error
import build.wallet.logging.LogLevel.Verbose
import co.touchlab.kermit.Severity
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import co.touchlab.kermit.Logger as KermitLogger

class LoggerTests : FunSpec({

  val logWriter = LogWriterMock()

  beforeTest {
    Logger.configure(
      tag = "DefaultTag",
      minimumLogLevel = Verbose,
      logWriters = listOf(logWriter)
    )
  }

  afterTest {
    logWriter.clear()
  }

  test("configure") {
    Logger.configure(
      tag = "DefaultTag",
      minimumLogLevel = Error,
      logWriters = listOf(logWriter)
    )

    KermitLogger.tag.shouldBe("DefaultTag")
    KermitLogger.config.run {
      minSeverity.shouldBe(Severity.Error)
      logWriterList.shouldContainOnly(logWriter)
    }
  }

  test("logVerbose with default tag") {
    logVerbose { "verbose message" }

    logWriter.expectLog(
      severity = Severity.Verbose,
      message = "verbose message",
      tag = "DefaultTag"
    )
  }

  test("logVerbose with custom tag") {
    logVerbose(tag = "SomeTag") { "verbose message" }

    logWriter.expectLog(
      severity = Severity.Verbose,
      message = "verbose message",
      tag = "SomeTag"
    )
  }

  test("logDebug with default tag") {
    logDebug { "debug message" }

    logWriter.expectLog(
      severity = Severity.Debug,
      message = "debug message",
      tag = "DefaultTag"
    )
  }

  test("logDebug with custom tag") {
    logDebug(tag = "SomeTag") { "debug message" }

    logWriter.expectLog(
      severity = Severity.Debug,
      message = "debug message",
      tag = "SomeTag"
    )
  }

  test("logInfo with default tag") {
    logInfo { "info message" }

    logWriter.expectLog(
      severity = Severity.Info,
      message = "info message",
      tag = "DefaultTag"
    )
  }

  test("logInfo with custom tag") {
    logInfo(tag = "SomeTag") { "info message" }

    logWriter.expectLog(
      severity = Severity.Info,
      message = "info message",
      tag = "SomeTag"
    )
  }

  test("logWarn - with default tag, no throwable") {
    logWarn { "warn message" }

    logWriter.expectLog(
      severity = Severity.Warn,
      message = "warn message",
      tag = "DefaultTag"
    )
  }

  test("logWarn - with custom tag, no throwable") {
    logWarn(tag = "SomeTag") { "warn message" }

    logWriter.expectLog(
      severity = Severity.Warn,
      message = "warn message",
      tag = "SomeTag"
    )
  }

  test("logWarn - with default tag, and with throwable") {
    val myException = Exception("oops")
    logWarn(throwable = myException) { "warn message" }

    logWriter.expectLog(
      severity = Severity.Warn,
      message = "warn message",
      throwable = myException,
      tag = "DefaultTag"
    )
  }

  test("logError - with default tag, no throwable") {
    logError { "error message" }

    logWriter.expectLog(
      severity = Severity.Error,
      message = "error message",
      tag = "DefaultTag"
    )
  }

  test("logError - with custom tag, no throwable") {
    logError(tag = "SomeTag") { "error message" }

    logWriter.expectLog(
      severity = Severity.Error,
      message = "error message",
      tag = "SomeTag"
    )
  }

  test("logError - with default tag, and with throwable") {
    val myException = Exception("oops")
    logError(throwable = myException) { "error message" }

    logWriter.expectLog(
      severity = Severity.Error,
      message = "error message",
      throwable = myException,
      tag = "DefaultTag"
    )
  }

  test("logAssert - with default tag, no throwable") {
    logAssert { "assert message" }

    logWriter.expectLog(
      severity = Severity.Assert,
      message = "assert message",
      tag = "DefaultTag"
    )
  }

  test("logAssert - with custom tag, no throwable") {
    logAssert(tag = "SomeTag") { "assert message" }

    logWriter.expectLog(
      severity = Severity.Assert,
      message = "assert message",
      tag = "SomeTag"
    )
  }

  test("logAssert - with default tag, and with throwable") {
    val myException = Exception("oops")
    logAssert(throwable = myException) { "assert message" }

    logWriter.expectLog(
      severity = Severity.Assert,
      message = "assert message",
      throwable = myException,
      tag = "DefaultTag"
    )
  }
})
