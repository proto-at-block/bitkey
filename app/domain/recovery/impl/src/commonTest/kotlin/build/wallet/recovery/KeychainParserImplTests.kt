package build.wallet.recovery

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

class KeychainParserImplTests : FunSpec({
  val parser = KeychainParser()

  test("parses valid spending key with derivation path prefix") {
    val entries = listOf(
      KeychainScanner.KeychainEntry(
        key = "secret-key:dpub123",
        value = "[4357ec3d/84'/1'/0']tprv8ZgxMBicQKsPeQQADibg4WF7mEasy3piWZUHyThAzJCPNgMHDVYhTCVfev8jFh4MNhhr2cZH4FUPnvXmLjpobuBVqBqfGyEv6pDjvqfvmCz"
      )
    )

    val result = parser.parse(entries)

    result.spendingKeys shouldHaveSize 1
    result.spendingKeys.first().dpub shouldBe "dpub123"
    result.spendingKeys.first().xprv shouldStartWith "tprv"
    result.spendingKeys.first().hasXprv shouldBe true
    result.spendingKeys.first().hasMnemonic shouldBe false
  }

  test("parses valid spending key without derivation path") {
    val entries = listOf(
      KeychainScanner.KeychainEntry(
        key = "secret-key:dpub456",
        value = "tprv8ZgxMBicQKsPeQQADibg4WF7mEasy3piWZUHyThAzJCPNgMHDVYhTCVfev8jFh4MNhhr2cZH4FUPnvXmLjpobuBVqBqfGyEv6pDjvqfvmCz"
      )
    )

    val result = parser.parse(entries)

    result.spendingKeys shouldHaveSize 1
    result.spendingKeys.first().xprv shouldStartWith "tprv"
  }

  test("parses valid mnemonic lengths (12 and 24 words)") {
    // Test boundary cases of VALID_MNEMONIC_WORD_COUNTS = setOf(12, 15, 18, 21, 24)
    val mnemonic12 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    val mnemonic24 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"

    val entries = listOf(
      KeychainScanner.KeychainEntry(
        key = "mnemonic:dpub12word",
        value = mnemonic12
      ),
      KeychainScanner.KeychainEntry(
        key = "mnemonic:dpub24word",
        value = mnemonic24
      )
    )

    val result = parser.parse(entries)

    result.spendingKeys shouldHaveSize 2
    result.spendingKeys.find { it.dpub == "dpub12word" }?.mnemonic shouldBe mnemonic12
    result.spendingKeys.find { it.dpub == "dpub24word" }?.mnemonic shouldBe mnemonic24
  }

  test("combines xprv and mnemonic for same dpub") {
    val entries = listOf(
      KeychainScanner.KeychainEntry(
        key = "secret-key:dpub123",
        value = "tprv8ZgxMBicQKsPeQQADibg4WF7mEasy3piWZUHyThAzJCPNgMHDVYhTCVfev8jFh4MNhhr2cZH4FUPnvXmLjpobuBVqBqfGyEv6pDjvqfvmCz"
      ),
      KeychainScanner.KeychainEntry(
        key = "mnemonic:dpub123",
        value = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
      )
    )

    val result = parser.parse(entries)

    result.spendingKeys shouldHaveSize 1
    result.spendingKeys.first().dpub shouldBe "dpub123"
    result.spendingKeys.first().hasXprv shouldBe true
    result.spendingKeys.first().hasMnemonic shouldBe true
  }

  test("parses valid auth key pair (compressed public key)") {
    val entries = listOf(
      KeychainScanner.KeychainEntry(
        key = "02b4632d08485ff1df2db55b9dafd23347d1c47a457072a1e87be26896549a8737",
        value = "8e812436a623ab3b5c1c0308de0fa90a81e3d6ba1c1c7c0e3e7e38f47c95c5c5"
      )
    )

    val result = parser.parse(entries)

    result.authKeys shouldHaveSize 1
    result.authKeys.first().value shouldBe "02b4632d08485ff1df2db55b9dafd23347d1c47a457072a1e87be26896549a8737"
    result.authKeyPrivates shouldContainKey "02b4632d08485ff1df2db55b9dafd23347d1c47a457072a1e87be26896549a8737"
  }

  test("validates xprv format requirements") {
    // Tests XPRV_PREFIXES_PATTERN and MIN_XPRV_LENGTH validation
    val entries = listOf(
      // Blank dpub - tests isBlank() check
      KeychainScanner.KeychainEntry(
        key = "secret-key:",
        value = "tprv8ZgxMBicQKsPeQQADibg4WF7mEasy3piWZUHyThAzJCPNgMHDVYhTCVfev8jFh4MNhhr2cZH4FUPnvXmLjpobuBVqBqfGyEv6pDjvqfvmCz"
      ),
      // Too short - tests MIN_XPRV_LENGTH (111 chars)
      KeychainScanner.KeychainEntry(
        key = "secret-key:dpub123",
        value = "tprv8short"
      ),
      // Wrong prefix - tests XPRV_PREFIXES_PATTERN regex
      KeychainScanner.KeychainEntry(
        key = "secret-key:dpub456",
        value = "xpubNotXprv8ZgxMBicQKsPeQQADibg4WF7mEasy3piWZUHyThAzJCPNgMHDVYhTCVfev8jFh4MNhhr2cZH4FUPnvXmLjpobuBVqBqfGyEv6pDjvqfvmCz"
      )
    )

    val result = parser.parse(entries)

    result.spendingKeys.shouldBeEmpty()
  }

  test("validates mnemonic format requirements") {
    // Tests VALID_MNEMONIC_WORD_COUNTS and MNEMONIC_WORD_PATTERN
    val entries = listOf(
      // Wrong word count (3 words, needs 12/15/18/21/24)
      KeychainScanner.KeychainEntry(
        key = "mnemonic:dpub123",
        value = "abandon abandon abandon"
      ),
      // Non-lowercase words - tests MNEMONIC_WORD_PATTERN regex
      KeychainScanner.KeychainEntry(
        key = "mnemonic:dpub456",
        value = "Abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
      )
    )

    val result = parser.parse(entries)

    result.spendingKeys.shouldBeEmpty()
  }

  test("validates auth key format and size requirements") {
    // Tests hex decoding and size validation (33/65 bytes for public, 32 bytes for private)
    val entries = listOf(
      // Wrong public key length (should be 33 or 65 bytes)
      KeychainScanner.KeychainEntry(
        key = "02b4632d08485ff1df", // Too short
        value = "8e812436a623ab3b5c1c0308de0fa90a81e3d6ba1c1c7c0e3e7e38f47c95c5c5"
      ),
      // Wrong private key length (should be 32 bytes)
      KeychainScanner.KeychainEntry(
        key = "02b4632d08485ff1df2db55b9dafd23347d1c47a457072a1e87be26896549a8737",
        value = "8e812436a623ab" // Too short
      ),
      // Invalid hex encoding - tests exception handling
      KeychainScanner.KeychainEntry(
        key = "not-valid-hex-zzz",
        value = "also-not-valid-hex"
      )
    )

    val result = parser.parse(entries)

    result.authKeys.shouldBeEmpty()
    result.authKeyPrivates.size shouldBe 0
  }

  test("parses mixed valid and invalid entries") {
    val entries = listOf(
      // Valid spending key
      KeychainScanner.KeychainEntry(
        key = "secret-key:dpub123",
        value = "tprv8ZgxMBicQKsPeQQADibg4WF7mEasy3piWZUHyThAzJCPNgMHDVYhTCVfev8jFh4MNhhr2cZH4FUPnvXmLjpobuBVqBqfGyEv6pDjvqfvmCz"
      ),
      // Invalid mnemonic (wrong word count)
      KeychainScanner.KeychainEntry(
        key = "mnemonic:dpub456",
        value = "abandon abandon"
      ),
      // Valid auth key
      KeychainScanner.KeychainEntry(
        key = "02b4632d08485ff1df2db55b9dafd23347d1c47a457072a1e87be26896549a8737",
        value = "8e812436a623ab3b5c1c0308de0fa90a81e3d6ba1c1c7c0e3e7e38f47c95c5c5"
      ),
      // Invalid xprv (too short)
      KeychainScanner.KeychainEntry(
        key = "secret-key:dpub789",
        value = "short"
      )
    )

    val result = parser.parse(entries)

    result.spendingKeys shouldHaveSize 1
    result.spendingKeys.first().dpub shouldBe "dpub123"
    result.authKeys shouldHaveSize 1
  }
})
