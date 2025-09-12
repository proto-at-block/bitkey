import build.wallet.logging.LogEntry
import build.wallet.logging.SensitiveDataResult
import build.wallet.logging.SensitiveDataResult.NoneFound
import build.wallet.logging.SensitiveDataResult.Sensitive
import build.wallet.logging.SensitiveDataValidator
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.types.shouldBeInstanceOf

class SensitiveDataValidatorTest : FunSpec({
  val bitcoinKeys = listOf(
    "xprv01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789",
    "tprv01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"
  )
  val bitcoinAddresses = listOf(
    "1Lbcfr7sAHTD9CgdQo3HTMTkV8LK4ZnX71",
    "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy", // P2SH
    "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq" // Bech32
  )

  test("detect bitcoin private key in log message") {
    bitcoinKeys.forEach {
      SensitiveDataValidator.check(
        LogEntry(
          tag = "SomeTag",
          message = "This contains a sensitive key material $it sandwiched in a log statement"
        )
      ).shouldViolateNamed("Bitcoin private key")
    }
  }

  test("detect bitcoin private key in log tag") {
    bitcoinKeys.forEach {
      SensitiveDataValidator.check(
        LogEntry(
          tag = "Some${it}tag",
          message = "This is a benign log message"
        )
      ).shouldViolateNamed("Bitcoin private key")
    }
  }

  test("detect bitcoin private key in log tag and message") {
    bitcoinKeys.forEach {
      SensitiveDataValidator.check(
        LogEntry(
          tag = "Some $it Tag",
          message = "This contains a sensitive key material $it sandwiched in a log statement"
        )
      ).shouldViolateNamed("Bitcoin private key")
    }
  }

  test("detect bitcoin address in log message") {
    bitcoinAddresses.forEach {
      SensitiveDataValidator.check(
        LogEntry(
          tag = "SomeTag",
          message = "This contains a sensitive key material $it sandwiched in a log statement"
        )
      ).shouldViolateNamed("Bitcoin addresses")
    }
  }

  test("detect bitcoin address in log tag") {
    bitcoinAddresses.forEach {
      SensitiveDataValidator.check(
        LogEntry(
          tag = "Some $it tag",
          message = "This is a benign log message"
        )
      ).shouldViolateNamed("Bitcoin addresses")
    }
  }

  test("detect name PII") {
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "Hello, Alice!")
    ).shouldViolateNamed("Common names/aliases")

    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "Hello, boB!")
    ).shouldViolateNamed("Common names/aliases")

    SensitiveDataValidator.check(
      LogEntry(tag = "ALICE", message = "Hello!")
    ).shouldViolateNamed("Common names/aliases")
  }

  test("detect aliases") {
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "Hello, Uncle!")
    ).shouldViolateNamed("Common names/aliases")

    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "Hello, uncLE!")
    ).shouldViolateNamed("Common names/aliases")

    SensitiveDataValidator.check(
      LogEntry(tag = "Uncle", message = "Hello!")
    ).shouldViolateNamed("Common names/aliases")
  }

  test("Contains a BIP-39 phrase") {
    // Plain Phrase
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "abandon ability able about above absent absorb abstract absurd abuse access accident")
    ).shouldViolateNamed("BIP-39 phrase")
    // Long Phrase
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "abandon ability able about above absent absorb abstract absurd abuse access accident account accuse achieve acid acoustic acquire across act action actor actress actual")
    ).shouldViolateNamed("BIP-39 phrase")
    // phrase separated by hyphens
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "ABANDON-ABILITY-ABLE-ABOUT-ABOVE-ABSENT-ABSORB-ABSTRACT-ABSURD-ABUSE-ACCESS-ACCIDENT")
    ).shouldViolateNamed("BIP-39 phrase")
    // phrase after description
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "Hey, check this out:abandon ability able about above absent absorb abstract absurd abuse access accident!!")
    ).shouldViolateNamed("BIP-39 phrase")

    // Common error message
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = """Error refreshing access token for active or onboarding keybox for FullAccountId(serverId=urn:wallet-account:0123456789ABCDEFGHIJKL). AuthNetworkError(message=Could not sign in: null, cause=NetworkError(cause=java.net.UnknownHostException: Unable to resolve host "api.bitkey.world": No address associated with hostname))""")
    ).shouldNotViolateIndicators()
    // text string
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "According to all known laws of aviation there is no way a bee should be able to fly")
    ).shouldNotViolateIndicators()
    // Unlikely formatting -- multiple symbols between words
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "abandon -- ability able about above absent absorb abstract absurd abuse access accident")
    ).shouldNotViolateIndicators()
  }

  test("Contains an recovery code") {
    // Lowest Possible recovery code:
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "4398046511104")
    ).shouldViolateNamed("Recovery Code")
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "4398-0465-1110-4")
    ).shouldViolateNamed("Recovery Code")
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "Code(4398046511104)")
    ).shouldViolateNamed("Recovery Code")
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "using code: 4398046511104 for invite")
    ).shouldViolateNamed("Recovery Code")

    // Too short:
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "Error 500: Internal Server Error")
    ).shouldNotViolateIndicators()
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "Current time: 1756227803")
    ).shouldNotViolateIndicators()

    // Millisecond timestamps:
    SensitiveDataValidator.check(
      LogEntry(tag = "SomeTag", message = "Current time: 1756227825703")
    ).shouldNotViolateIndicators()
  }

  test("no sensitive data detected") {
    val nonSensitiveLogEntry = LogEntry(tag = "RegularTag", message = "Just a regular log message")
    SensitiveDataValidator.check(nonSensitiveLogEntry).shouldNotViolateIndicators()
  }
})

private fun SensitiveDataResult.shouldViolateNamed(name: String) {
  withClue("Sensitive data should violate indicator named: [$name]") {
    shouldBeInstanceOf<Sensitive>()
      .violations.any { it.name == name }
      .shouldBeTrue()
  }
}

private fun SensitiveDataResult.shouldNotViolateIndicators() {
  withClue("Sensitive data should not be found") {
    shouldBeInstanceOf<NoneFound>()
  }
}
