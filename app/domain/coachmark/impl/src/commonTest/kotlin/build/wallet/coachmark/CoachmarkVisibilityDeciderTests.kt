package build.wallet.coachmark

import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.flags.Bip177FeatureFlag
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryFake
import build.wallet.onboarding.OnboardingCompletionServiceFake
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class CoachmarkVisibilityDeciderTests :
  FunSpec({
    val clock = ClockFake()
    val bip177CoachmarkPolicy = Bip177CoachmarkPolicy(
      clock = clock,
      bip177FeatureFlag = Bip177FeatureFlag(FeatureFlagDaoMock()),
      bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake(),
      bip177CoachmarkEligibilityDao = Bip177CoachmarkEligibilityDaoFake(),
      onboardingCompletionService = OnboardingCompletionServiceFake()
    )

    val coachmarkVisibilityDecider = CoachmarkVisibilityDecider(
      clock = clock,
      bip177CoachmarkPolicy = bip177CoachmarkPolicy
    )

    test("return unexpired coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.PrivateWalletHomeCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("return unviewed coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.PrivateWalletHomeCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("return feature flag on coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.PrivateWalletHomeCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("don't return expired coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.PrivateWalletHomeCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_PAST
        )
      ).shouldBe(false)
    }

    test("don't return viewed coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.PrivateWalletHomeCoachmark,
          viewed = true,
          expiration = Instant.DISTANT_PAST
        )
      ).shouldBe(false)
    }

    test("non-BIP177 coachmark should be created by default") {
      coachmarkVisibilityDecider.shouldCreate(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
        .shouldBe(true)
    }
  })
