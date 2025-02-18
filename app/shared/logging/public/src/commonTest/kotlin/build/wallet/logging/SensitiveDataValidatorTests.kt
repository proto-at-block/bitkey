import build.wallet.logging.LogEntry
import build.wallet.logging.SensitiveDataValidator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SensitiveDataValidatorTest : FunSpec({
  val bitcoinKeys = listOf(
    "xprv01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789",
    "tprv01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"
  )
  test("detect bitcoin private key in log message") {
    bitcoinKeys.forEach {
      SensitiveDataValidator.isSensitiveData(
        LogEntry(
          tag = "SomeTag",
          message = "This contains a sensitive key material $it sandwiched in a log statement"
        )
      ).shouldBe(true)
    }
  }

  test("detect bitcoin private key in log tag") {
    bitcoinKeys.forEach {
      SensitiveDataValidator.isSensitiveData(
        LogEntry(
          tag = "Some${it}tag",
          message = "This is a benign log message"
        )
      ).shouldBe(true)
    }
  }

  test("detect bitcoin private key in log tag and message") {
    bitcoinKeys.forEach {
      SensitiveDataValidator.isSensitiveData(
        LogEntry(
          tag = "Some $it Tag",
          message = "This contains a sensitive key material $it sandwiched in a log statement"
        )
      ).shouldBe(true)
    }
  }

  test("detect name PII") {
    SensitiveDataValidator.isSensitiveData(
      LogEntry(tag = "SomeTag", message = "Hello, Alice!")
    ).shouldBe(true)

    SensitiveDataValidator.isSensitiveData(
      LogEntry(tag = "SomeTag", message = "Hello, boB!")
    ).shouldBe(true)

    SensitiveDataValidator.isSensitiveData(
      LogEntry(tag = "ALICE", message = "Hello!")
    ).shouldBe(true)
  }

  test("detect aliases") {
    SensitiveDataValidator.isSensitiveData(
      LogEntry(tag = "SomeTag", message = "Hello, Uncle!")
    ).shouldBe(true)

    SensitiveDataValidator.isSensitiveData(
      LogEntry(tag = "SomeTag", message = "Hello, uncLE!")
    ).shouldBe(true)

    SensitiveDataValidator.isSensitiveData(
      LogEntry(tag = "Uncle", message = "Hello!")
    ).shouldBe(true)
  }

  test("no sensitive data detected") {
    val nonSensitiveLogEntry = LogEntry(tag = "RegularTag", message = "Just a regular log message")
    SensitiveDataValidator.isSensitiveData(nonSensitiveLogEntry).shouldBe(false)
  }
})
