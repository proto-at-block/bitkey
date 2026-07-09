package bitkey.ui.screens.securityhub

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class SecurityHubBodyModelTests : FunSpec({
  test("all-set pill is hidden when scrolled to its hide threshold") {
    isAllSetPillHidden(
      scrollValue = 64,
      allSetScrollTargetPx = 64
    ).shouldBeTrue()
  }

  test("all-set pill is not hidden when there is no hide threshold") {
    isAllSetPillHidden(
      scrollValue = 0,
      allSetScrollTargetPx = 0
    ).shouldBeFalse()
  }

  test("hide-on-entry scroll only runs when the pill is not already offscreen") {
    shouldScrollToHideAllSetPillOnEntry(
      scrollValue = 0,
      allSetScrollTargetPx = 64
    ).shouldBeTrue()

    shouldScrollToHideAllSetPillOnEntry(
      scrollValue = 64,
      allSetScrollTargetPx = 64
    ).shouldBeFalse()

    shouldScrollToHideAllSetPillOnEntry(
      scrollValue = 96,
      allSetScrollTargetPx = 64
    ).shouldBeFalse()
  }

  test("reveal haptic fires only when the hidden pill is fully revealed again") {
    shouldTriggerAllSetPillRevealHaptic(
      shouldRenderAllSetPill = true,
      allSetScrollTargetPx = 64,
      scrollValue = 12,
      hasSeenHiddenAllSetPill = true
    ).shouldBeFalse()

    shouldTriggerAllSetPillRevealHaptic(
      shouldRenderAllSetPill = true,
      allSetScrollTargetPx = 64,
      scrollValue = 0,
      hasSeenHiddenAllSetPill = true
    ).shouldBeTrue()
  }

  test("reveal haptic does not fire if the pill has not been hidden first") {
    shouldTriggerAllSetPillRevealHaptic(
      shouldRenderAllSetPill = true,
      allSetScrollTargetPx = 64,
      scrollValue = 0,
      hasSeenHiddenAllSetPill = false
    ).shouldBeFalse()
  }

  test("already-hidden all-set pill skips the auto-hide animation replay") {
    shouldAnimateAllSetPillAutoHide(
      scrollValue = 64,
      allSetScrollTargetPx = 64
    ).shouldBeFalse()

    shouldAnimateAllSetPillAutoHide(
      scrollValue = 48,
      allSetScrollTargetPx = 64
    ).shouldBeTrue()
  }

  test("reveal resistance applies only while dragging down through the hidden-pill range") {
    shouldApplyAllSetPillRevealResistance(
      shouldRenderAllSetPill = true,
      allSetScrollTargetPx = 64,
      scrollValue = 64,
      availableScrollDeltaY = 12f
    ).shouldBeTrue()

    shouldApplyAllSetPillRevealResistance(
      shouldRenderAllSetPill = true,
      allSetScrollTargetPx = 64,
      scrollValue = 80,
      availableScrollDeltaY = 12f
    ).shouldBeFalse()

    shouldApplyAllSetPillRevealResistance(
      shouldRenderAllSetPill = true,
      allSetScrollTargetPx = 64,
      scrollValue = 12,
      availableScrollDeltaY = -12f
    ).shouldBeFalse()
  }

  test("reveal resistance has an activation threshold before the pill starts moving") {
    calculateAllSetPillRevealResistanceResult(
      availableScrollDeltaY = 20f,
      remainingActivationPullPx = 24f,
      scrollValue = 64,
      allSetScrollTargetPx = 64
    ).consumedScrollY.shouldBe(20f)

    calculateAllSetPillRevealResistanceResult(
      availableScrollDeltaY = 20f,
      remainingActivationPullPx = 24f,
      scrollValue = 64,
      allSetScrollTargetPx = 64
    ).remainingActivationPullPx.shouldBe(4f)
  }

  test("reveal resistance stays heavy right after the pill starts coming back") {
    calculateAllSetPillRevealResistanceResult(
      availableScrollDeltaY = 20f,
      remainingActivationPullPx = 8f,
      scrollValue = 64,
      allSetScrollTargetPx = 64
    ).consumedScrollY.shouldBe(18.56f)

    calculateAllSetPillRevealResistanceResult(
      availableScrollDeltaY = 20f,
      remainingActivationPullPx = 8f,
      scrollValue = 64,
      allSetScrollTargetPx = 64
    ).remainingActivationPullPx.shouldBe(0f)
  }

  test("reveal activation threshold scales with the hidden-pill distance") {
    calculateAllSetPillRevealActivationThresholdPx(64).shouldBe(38.4f)
  }

  test("reveal resistance is strongest while the pill is still mostly hidden") {
    calculateAllSetPillRevealResistanceConsumption(
      availableScrollDeltaY = 20f,
      scrollValue = 64,
      allSetScrollTargetPx = 64
    ).shouldBeGreaterThan(
      calculateAllSetPillRevealResistanceConsumption(
        availableScrollDeltaY = 20f,
        scrollValue = 16,
        allSetScrollTargetPx = 64
      )
    )
  }
})
