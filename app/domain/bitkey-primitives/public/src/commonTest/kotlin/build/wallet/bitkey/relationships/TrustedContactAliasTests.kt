package build.wallet.bitkey.relationships

import com.github.michaelbull.result.getError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class TrustedContactAliasTests : FunSpec({
  test("valid plain names") {
    for (name in listOf("Alice", "Bob Smith", "Jean-Pierre", "O'Brien")) {
      TrustedContactAlias.validate(name).isOk shouldBe true
    }
  }

  test("valid accented names") {
    for (name in listOf("Müller", "José", "Ångström", "Ōsaka")) {
      TrustedContactAlias.validate(name).isOk shouldBe true
    }
  }

  test("valid names with digits and punctuation") {
    for (name in listOf("Agent 007", "Test (1)", "A. B. C.")) {
      TrustedContactAlias.validate(name).isOk shouldBe true
    }
  }

  test("empty and blank are valid (handled by button enable state)") {
    TrustedContactAlias.validate("").isOk shouldBe true
    TrustedContactAlias.validate("   ").isOk shouldBe true
  }

  test("rejects emoji") {
    TrustedContactAlias.validate("Alice 👋").getError()
      .shouldNotBeNull()
      .shouldContain("letters")
  }

  test("rejects emoji-only") {
    TrustedContactAlias.validate("🎉🔥").isErr shouldBe true
  }

  test("rejects CJK characters") {
    TrustedContactAlias.validate("田中太郎").isErr shouldBe true
  }

  test("rejects Cyrillic") {
    TrustedContactAlias.validate("Пётр").isErr shouldBe true
  }

  test("rejects non-breaking space and soft hyphen") {
    TrustedContactAlias.validate("\u00A0").isErr shouldBe true
    TrustedContactAlias.validate("Alice\u00ADSmith").isErr shouldBe true
  }

  test("rejects names exceeding max length") {
    TrustedContactAlias.validate("A".repeat(TrustedContactAlias.MAX_LENGTH + 1)).getError()
      .shouldNotBeNull()
      .shouldContain("${TrustedContactAlias.MAX_LENGTH}")
  }

  test("accepts names at max length") {
    val maxName = "A".repeat(TrustedContactAlias.MAX_LENGTH)
    TrustedContactAlias.validate(maxName).isOk shouldBe true
  }
})
