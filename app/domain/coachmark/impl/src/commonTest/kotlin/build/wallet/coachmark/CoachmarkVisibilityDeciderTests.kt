package build.wallet.coachmark

import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryFake
import build.wallet.onboarding.OnboardingCompletionServiceFake
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

class CoachmarkVisibilityDeciderTests :
  FunSpec({
    val clock = ClockFake()
    val onboardingCompletionService = OnboardingCompletionServiceFake()
    val bip177CoachmarkPolicy = Bip177CoachmarkPolicy(
      clock = clock,
      bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake(),
      bip177CoachmarkEligibilityDao = Bip177CoachmarkEligibilityDaoFake(),
      onboardingCompletionService = OnboardingCompletionServiceFake()
    )

    val coachmarkVisibilityDecider = CoachmarkVisibilityDecider(
      clock = clock,
      bip177CoachmarkPolicy = bip177CoachmarkPolicy,
      onboardingCompletionService = onboardingCompletionService
    )

    beforeTest {
      onboardingCompletionService.reset()
      // The W3 upgrade blocker coachmark requires onboarding completion 14+ days ago. Default
      // to a long-past completion so tests exercise other criteria; time-gating tests override.
      onboardingCompletionService.completionTimestamp = Instant.DISTANT_PAST
      onboardingCompletionService.getCompletionTimestampResult = Ok(Instant.DISTANT_PAST)
    }

    test("return unexpired coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.W3UpgradeBlockerCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("return unviewed coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.W3UpgradeBlockerCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("PrivateWalletHomeCoachmark is hard-coded off") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.PrivateWalletHomeCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(false)
    }

    test("don't return expired coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.W3UpgradeBlockerCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_PAST
        )
      ).shouldBe(false)
    }

    test("don't return viewed coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.W3UpgradeBlockerCoachmark,
          viewed = true,
          expiration = Instant.DISTANT_PAST
        )
      ).shouldBe(false)
    }

    test("W3UpgradeBlockerCoachmark is ineligible when no onboarding timestamp") {
      // No timestamp (pre-feature install or fresh recovery): not eligible yet. Money Home
      // records a timestamp on first render, which starts the 14-day delay window.
      onboardingCompletionService.completionTimestamp = null
      onboardingCompletionService.getCompletionTimestampResult = Ok(null)
      coachmarkVisibilityDecider.shouldCreate(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)
        .shouldBe(false)
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(CoachmarkIdentifier.W3UpgradeBlockerCoachmark, viewed = false, expiration = null)
      ).shouldBe(false)
    }

    test("W3UpgradeBlockerCoachmark is eligible when 14+ days since onboarding") {
      onboardingCompletionService.getCompletionTimestampResult = Ok(clock.now() - 15.days)
      coachmarkVisibilityDecider.shouldCreate(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)
        .shouldBe(true)
      // Re-set because the fake resets after first read
      onboardingCompletionService.getCompletionTimestampResult = Ok(clock.now() - 15.days)
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(CoachmarkIdentifier.W3UpgradeBlockerCoachmark, viewed = false, expiration = null)
      ).shouldBe(true)
    }

    test("W3UpgradeBlockerCoachmark is ineligible when less than 14 days since onboarding") {
      onboardingCompletionService.getCompletionTimestampResult = Ok(clock.now() - 5.days)
      coachmarkVisibilityDecider.shouldCreate(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)
        .shouldBe(false)
      // Re-set because the fake resets after first read
      onboardingCompletionService.getCompletionTimestampResult = Ok(clock.now() - 5.days)
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(CoachmarkIdentifier.W3UpgradeBlockerCoachmark, viewed = false, expiration = null)
      ).shouldBe(false)
    }
  })
