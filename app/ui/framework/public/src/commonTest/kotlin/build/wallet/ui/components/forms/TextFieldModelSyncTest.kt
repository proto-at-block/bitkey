package build.wallet.ui.components.forms

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class TextFieldModelSyncTest : FunSpec({

  test("initial model value is not adopted") {
    val sync = TextFieldModelSync("hello")
    sync.shouldAdoptExternal("hello").shouldBeFalse()
  }

  test("local edit is reported upward once") {
    val sync = TextFieldModelSync("")
    sync.onLocalTextChanged("h").shouldBeTrue()
    // Re-observing the same text (e.g. selection-only change) is not re-reported.
    sync.onLocalTextChanged("h").shouldBeFalse()
  }

  test("exact echo of latest edit is ignored") {
    val sync = TextFieldModelSync("")
    sync.onLocalTextChanged("h").shouldBeTrue()
    sync.shouldAdoptExternal("h").shouldBeFalse()
  }

  test("stale echo of intermediate edit is ignored - rapid typing") {
    val sync = TextFieldModelSync("")
    // User types faster than the producer echoes.
    sync.onLocalTextChanged("h").shouldBeTrue()
    sync.onLocalTextChanged("he").shouldBeTrue()
    sync.onLocalTextChanged("hel").shouldBeTrue()
    // Echoes arrive late, one per edit. None may clobber local state.
    sync.shouldAdoptExternal("h").shouldBeFalse()
    sync.shouldAdoptExternal("he").shouldBeFalse()
    sync.shouldAdoptExternal("hel").shouldBeFalse()
  }

  test("genuine external correction is adopted") {
    val sync = TextFieldModelSync("")
    sync.onLocalTextChanged("5551234").shouldBeTrue()
    // Producer reformats the phone number.
    sync.shouldAdoptExternal("555-1234").shouldBeTrue()
    // And the adopted value round-trips as an echo, not a new edit.
    sync.onLocalTextChanged("555-1234").shouldBeFalse()
  }

  test("programmatic clear is adopted") {
    val sync = TextFieldModelSync("")
    sync.onLocalTextChanged("some text").shouldBeTrue()
    sync.shouldAdoptExternal("some text").shouldBeFalse()
    // Producer resets the field (e.g. after submit).
    sync.shouldAdoptExternal("").shouldBeTrue()
  }

  test("external correction after stale echoes is still adopted") {
    val sync = TextFieldModelSync("")
    sync.onLocalTextChanged("a").shouldBeTrue()
    sync.onLocalTextChanged("ab").shouldBeTrue()
    sync.shouldAdoptExternal("a").shouldBeFalse()
    // Producer rejects "ab" and corrects to "AB".
    sync.shouldAdoptExternal("AB").shouldBeTrue()
  }

  test("adoption updates source of truth for future echoes") {
    val sync = TextFieldModelSync("")
    sync.onLocalTextChanged("x").shouldBeTrue()
    sync.shouldAdoptExternal("X").shouldBeTrue()
    // Repeated recompositions with the same corrected value are no-ops.
    sync.shouldAdoptExternal("X").shouldBeFalse()
    sync.shouldAdoptExternal("X").shouldBeFalse()
  }

  test("conflated echoes - producer catches up skipping intermediate values") {
    val sync = TextFieldModelSync("")
    // Compose snapshot state conflates rapid updates: intermediate echoes may be
    // skipped entirely, but never delivered out of order.
    sync.onLocalTextChanged("a").shouldBeTrue()
    sync.onLocalTextChanged("ab").shouldBeTrue()
    sync.onLocalTextChanged("abc").shouldBeTrue()
    // Only the latest echo arrives; "a" and "ab" were conflated away.
    sync.shouldAdoptExternal("abc").shouldBeFalse()
    // Older pending echoes are settled; a later producer value equal to an old
    // edit is, by design, a genuine correction (producer reverting the edit).
    sync.shouldAdoptExternal("a").shouldBeTrue()
    sync.onLocalTextChanged("a").shouldBeFalse()
  }

  test("producer reverting to previously typed text is adopted as correction") {
    val sync = TextFieldModelSync("")
    sync.onLocalTextChanged("valid").shouldBeTrue()
    sync.shouldAdoptExternal("valid").shouldBeFalse()
    sync.onLocalTextChanged("valid!").shouldBeTrue()
    sync.shouldAdoptExternal("valid!").shouldBeFalse()
    // Producer rejects the "!" and reverts to the earlier accepted value.
    sync.shouldAdoptExternal("valid").shouldBeTrue()
  }

  test("user deleting back to a previously reported value is still reported") {
    val sync = TextFieldModelSync("")
    sync.onLocalTextChanged("a").shouldBeTrue()
    sync.onLocalTextChanged("ab").shouldBeTrue()
    sync.shouldAdoptExternal("ab").shouldBeFalse()
    // Backspace to "a" — must be reported even though "a" was reported before.
    sync.onLocalTextChanged("a").shouldBeTrue()
    sync.shouldAdoptExternal("a").shouldBeFalse()
  }
})
