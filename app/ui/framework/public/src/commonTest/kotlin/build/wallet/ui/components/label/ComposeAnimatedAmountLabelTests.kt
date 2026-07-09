package build.wallet.ui.components.label

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ComposeAnimatedAmountLabelTests : FunSpec({
  test("transition mask only animates the changed middle digits") {
    val mask = buildAnimatedAmountTransitionMask(previous = "$12,345.67", current = "$12,346.67")

    mask.enterAnimatedCharacters.toList() shouldBe listOf(
      false,
      false,
      false,
      false,
      false,
      false,
      true,
      false,
      false,
      false
    )
    mask.exitAnimatedCharacters.toList() shouldBe listOf(
      false,
      false,
      false,
      false,
      false,
      false,
      true,
      false,
      false,
      false
    )
  }

  test("transition mask animates the whole value when either side is empty") {
    val mask = buildAnimatedAmountTransitionMask(previous = "", current = "$123")

    mask.enterAnimatedCharacters.toList() shouldBe listOf(true, true, true, true)
    mask.exitAnimatedCharacters.toList() shouldBe emptyList()
  }

  test("amount direction follows value changes") {
    animatedAmountDirectionFor(previousValue = 100L, currentValue = 101L)
      .shouldBe(AnimatedAmountDirection.Increase)

    animatedAmountDirectionFor(previousValue = 100L, currentValue = 99L)
      .shouldBe(AnimatedAmountDirection.Decrease)

    animatedAmountDirectionFor(previousValue = 100L, currentValue = 100L)
      .shouldBe(AnimatedAmountDirection.Increase)
  }

  test("amount direction changes vertical animation motion") {
    animatedAmountDrawState(
      elapsedMs = 0f,
      durationMs = 350,
      groupIndex = 0,
      textHeight = 40f,
      direction = AnimatedAmountDirection.Increase,
      phase = AnimatedAmountPhase.Enter
    ).translateY shouldBe 20f

    animatedAmountDrawState(
      elapsedMs = 0f,
      durationMs = 350,
      groupIndex = 0,
      textHeight = 40f,
      direction = AnimatedAmountDirection.Decrease,
      phase = AnimatedAmountPhase.Enter
    ).translateY shouldBe -20f

    animatedAmountDrawState(
      elapsedMs = 350f,
      durationMs = 350,
      groupIndex = 0,
      textHeight = 40f,
      direction = AnimatedAmountDirection.Increase,
      phase = AnimatedAmountPhase.Exit
    ).translateY shouldBe -20f

    animatedAmountDrawState(
      elapsedMs = 350f,
      durationMs = 350,
      groupIndex = 0,
      textHeight = 40f,
      direction = AnimatedAmountDirection.Decrease,
      phase = AnimatedAmountPhase.Exit
    ).translateY shouldBe 20f
  }

  test("min scale accounts for disabled font scaling") {
    resolvedAnimatedAmountMinScale(
      styleFontSize = 8.sp,
      minTextSize = 12.sp,
      allowFontScaling = false,
      fontScale = 2f
    ) shouldBe 0.75f
  }

  test("horizontal offset keeps narrower text aligned during transitions") {
    animatedAmountHorizontalOffset(
      alignment = TextAlign.Start,
      contentWidth = 100f,
      layoutWidth = 80f
    ) shouldBe 0f

    animatedAmountHorizontalOffset(
      alignment = TextAlign.Center,
      contentWidth = 100f,
      layoutWidth = 80f
    ) shouldBe 10f

    animatedAmountHorizontalOffset(
      alignment = TextAlign.End,
      contentWidth = 100f,
      layoutWidth = 80f
    ) shouldBe 20f
  }
})
