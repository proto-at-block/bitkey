import build.wallet.logging.LogEntry
import build.wallet.logging.SensitiveDataValidator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SensitiveDataValidatorTest : FunSpec({
  test("should return true for log message containing sensitive data") {
    // Assuming LogEntry is a class with a tag and message you're testing against
    val sensitiveLogEntry = LogEntry(
      tag = "SomeTag",
      message =
        "This contains a sensitive key material xprv012345678901234567890123456789" +
          "012345678901234567890123456789012345678901234567890123456789" +
          "01234567890123456789 sandwiched in a log statement"
    )
    SensitiveDataValidator.isSensitiveData(sensitiveLogEntry).shouldBe(true)
  }

  test("should return true for log tag containing sensitive data") {
    // Assuming LogEntry is a class with a tag and message you're testing against
    val sensitiveLogEntry = LogEntry(
      tag = "Somexprv01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789Tag",
      message =
        "This is a benign log message"
    )
    SensitiveDataValidator.isSensitiveData(sensitiveLogEntry).shouldBe(true)
  }

  test("should return true for log tag and message containing sensitive data") {
    // Assuming LogEntry is a class with a tag and message you're testing against
    val sensitiveLogEntry = LogEntry(
      tag = "Some xprv01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789 Tag",
      message =
        "This contains a sensitive key material xprv012345678901234567890123456789" +
          "012345678901234567890123456789012345678901234567890123456789" +
          "01234567890123456789 sandwiched in a log statement"
    )
    SensitiveDataValidator.isSensitiveData(sensitiveLogEntry).shouldBe(true)
  }

  test("should return false for log entries not containing sensitive data") {
    val nonSensitiveLogEntry = LogEntry(tag = "RegularTag", message = "Just a regular log message")
    SensitiveDataValidator.isSensitiveData(nonSensitiveLogEntry).shouldBe(false)
  }
})
