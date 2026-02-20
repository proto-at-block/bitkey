package bitkey.primitives

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class UnicodeExtensionsTest : FunSpec({

  test("codePointSequence - basic ASCII") {
    val codePoints = "ABC".codePointSequence().toList()
    codePoints.shouldContainExactly(0x41, 0x42, 0x43) // A, B, C
  }

  test("codePointSequence - with emoji") {
    // 😀 is U+1F600, which requires a surrogate pair
    val codePoints = "A😀B".codePointSequence().toList()
    codePoints.shouldContainExactly(0x41, 0x1F600, 0x42)
  }

  test("codePointSequence - regional indicator symbols (flag)") {
    // 🇺🇸 is made of two regional indicators: U+1F1FA (U) and U+1F1F8 (S)
    val codePoints = "\uD83C\uDDFA\uD83C\uDDF8".codePointSequence().toList()
    codePoints.shouldContainExactly(0x1F1FA, 0x1F1F8)
  }

  test("codePointSequence - empty string") {
    val codePoints = "".codePointSequence().toList()
    codePoints.shouldBe(emptyList())
  }

  test("appendCodePoint - basic ASCII") {
    val result = StringBuilder()
      .appendCodePoint(0x48) // H
      .appendCodePoint(0x69) // i
      .toString()

    result.shouldBe("Hi")
  }

  test("appendCodePoint - emoji") {
    val result = StringBuilder()
      .appendCodePoint(0x1F600) // 😀
      .toString()

    result.shouldBe("😀")
  }

  test("appendCodePoint - regional indicators for flag") {
    // Generate US flag: 🇺🇸
    val result = StringBuilder()
      .appendCodePoint(0x1F1FA) // Regional Indicator Symbol Letter U
      .appendCodePoint(0x1F1F8) // Regional Indicator Symbol Letter S
      .toString()

    result.shouldBe("🇺🇸")
  }

  test("appendCodePoint - multiple emojis and text") {
    val result = StringBuilder()
      .appendCodePoint(0x48) // H
      .appendCodePoint(0x1F600) // 😀
      .appendCodePoint(0x21) // !
      .toString()

    result.shouldBe("H😀!")
  }

  test("round trip - convert country code to flag emoji") {
    // This is the actual use case from FiatCurrency
    val alpha2Code = "US"
    val asciiToIndicatorOffset = 127397

    val flagEmoji = StringBuilder().apply {
      alpha2Code.codePointSequence().forEach { codePoint ->
        appendCodePoint(codePoint + asciiToIndicatorOffset)
      }
    }.toString()

    flagEmoji.shouldBe("🇺🇸")
  }

  test("round trip - EU flag") {
    val alpha2Code = "EU"
    val asciiToIndicatorOffset = 127397

    val flagEmoji = StringBuilder().apply {
      alpha2Code.codePointSequence().forEach { codePoint ->
        appendCodePoint(codePoint + asciiToIndicatorOffset)
      }
    }.toString()

    flagEmoji.shouldBe("🇪🇺")
  }

  test("round trip - various country codes") {
    val countryCodes = listOf("GB", "JP", "FR", "DE", "CA")
    val expectedFlags = listOf("🇬🇧", "🇯🇵", "🇫🇷", "🇩🇪", "🇨🇦")

    countryCodes.forEachIndexed { index, code ->
      val asciiToIndicatorOffset = 127397
      val flagEmoji = StringBuilder().apply {
        code.codePointSequence().forEach { codePoint ->
          appendCodePoint(codePoint + asciiToIndicatorOffset)
        }
      }.toString()

      flagEmoji.shouldBe(expectedFlags[index])
    }
  }
})
