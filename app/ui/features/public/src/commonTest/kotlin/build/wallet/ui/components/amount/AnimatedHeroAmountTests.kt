package build.wallet.ui.components.amount

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AnimatedHeroAmountTests : FunSpec({
  test("matches repeated digits when thousands separators are inserted") {
    matchCharacterIndices(
      previous = "999",
      current = "9,999"
    ).shouldBe(
      listOf(
        CharacterIndexMatch(previousIndex = 0, currentIndex = 0),
        CharacterIndexMatch(previousIndex = 1, currentIndex = 2),
        CharacterIndexMatch(previousIndex = 2, currentIndex = 3)
      )
    )
  }

  test("preserves stable characters when deleting a trailing fractional digit") {
    matchCharacterIndices(
      previous = "$10.00",
      current = "$10.0"
    ).shouldBe(
      listOf(
        CharacterIndexMatch(previousIndex = 0, currentIndex = 0),
        CharacterIndexMatch(previousIndex = 1, currentIndex = 1),
        CharacterIndexMatch(previousIndex = 2, currentIndex = 2),
        CharacterIndexMatch(previousIndex = 3, currentIndex = 3),
        CharacterIndexMatch(previousIndex = 4, currentIndex = 4)
      )
    )
  }

  test("prefers the decimal separator over repeated digit matches during rollovers") {
    matchCharacterIndices(
      previous = "$0.99",
      current = "$1.00"
    ).shouldBe(
      listOf(
        CharacterIndexMatch(previousIndex = 0, currentIndex = 0),
        CharacterIndexMatch(previousIndex = 2, currentIndex = 2)
      )
    )
  }

  test("treats empty inputs as having no character matches") {
    matchCharacterIndices(
      previous = "",
      current = "$0"
    ).shouldBe(emptyList())
  }

  test("vertical motion uses entry offset while glyphs enter or exit") {
    animatedAmountVerticalTranslationTarget(
      entering = true,
      exiting = false,
      entryTranslationYPx = 16f
    ).shouldBe(16f)

    animatedAmountVerticalTranslationTarget(
      entering = false,
      exiting = true,
      entryTranslationYPx = 16f
    ).shouldBe(16f)

    animatedAmountVerticalTranslationTarget(
      entering = false,
      exiting = false,
      entryTranslationYPx = 16f
    ).shouldBe(0f)
  }

  test("draw start offset centers content when requested") {
    animatedAmountStartX(
      containerWidth = 200f,
      contentWidth = 80f,
      alignStart = false
    ).shouldBe(60f)
  }

  test("draw start offset honors start alignment when requested") {
    animatedAmountStartX(
      containerWidth = 200f,
      contentWidth = 80f,
      alignStart = true
    ).shouldBe(0f)
  }
})
