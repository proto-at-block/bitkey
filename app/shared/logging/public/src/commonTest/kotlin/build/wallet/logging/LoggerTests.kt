package build.wallet.logging

import build.wallet.logging.LogLevel.Assert
import build.wallet.logging.LogLevel.Debug
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.LogLevel.Info
import build.wallet.logging.LogLevel.Verbose
import build.wallet.logging.LogLevel.Warn
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe

class LoggerTests : FunSpec({

  val logWriter = LogWriterMock()

  beforeTest {
    Logger.configure(
      tag = "build.wallet",
      minimumLogLevel = Verbose,
      logWriters = listOf(logWriter)
    )
  }

  afterTest {
    logWriter.clear()
  }

  test("configure") {
    Logger.configure(
      tag = "build.wallet",
      minimumLogLevel = Error,
      logWriters = listOf(logWriter)
    )

    KermitLogger.tag.shouldBe("build.wallet")
    KermitLogger.config.run {
      minSeverity.shouldBe(KermitSeverity.Error)
      logWriterList.shouldContainOnly(logWriter)
    }
  }

  test("log default") {
    log { "default" }

    logWriter.expectLog(
      tag = "build.wallet",
      severity = KermitSeverity.Info,
      message = "default"
    )
  }

  LogLevel.entries.forEach { logLevel ->
    test("log severity ${logLevel.name}") {
      log(logLevel) { "$severity message" }

      logWriter.expectLog(
        severity =
          when (logLevel) {
            Verbose -> KermitSeverity.Verbose
            Debug -> KermitSeverity.Debug
            Info -> KermitSeverity.Info
            Warn -> KermitSeverity.Warn
            Error -> KermitSeverity.Error
            Assert -> KermitSeverity.Assert
          },
        message = "$severity message"
      )
    }
  }

  test("log with throwable - no level provided") {
    val myException = Exception("oops")

    log(throwable = myException) { "exception!" }

    logWriter.expectLog(
      severity = KermitSeverity.Error,
      message = "exception!",
      throwable = myException
    )
  }

  test("log with throwable - level provided") {
    val myException = Exception("oops")

    log(Warn, throwable = myException) { "exception!" }

    logWriter.expectLog(
      severity = KermitSeverity.Warn,
      message = "exception!",
      throwable = myException
    )
  }

  test("log with tag") {
    log(tag = "jack was here") { "hello!" }

    logWriter.expectLog(
      severity = KermitSeverity.Info,
      tag = "jack was here",
      message = "hello!"
    )
  }
})
