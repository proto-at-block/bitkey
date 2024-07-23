package build.wallet.coachmark

import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.InAppSecurityFeatureFlag
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class CoachmarkVisibilityDeciderTests :
  FunSpec({

    val featureFlagDao = FeatureFlagDaoMock()
    val inAppSecurityFlag = InAppSecurityFeatureFlag(featureFlagDao)
    val coachmarkVisibilityDecider = CoachmarkVisibilityDecider(
      inAppSecurityFeatureFlag = inAppSecurityFlag,
      ClockFake()
    )

    beforeTest {
      inAppSecurityFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    }

    test("return unexpired coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.HiddenBalanceCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("return unviewed coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.HiddenBalanceCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("return feature flag on coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.HiddenBalanceCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("don't return expired coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.HiddenBalanceCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_PAST
        )
      ).shouldBe(false)
    }

    test("don't return viewed coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.HiddenBalanceCoachmark,
          viewed = true,
          expiration = Instant.DISTANT_PAST
        )
      ).shouldBe(false)
    }

    test("don't return feature flagged coachmarks that are off") {
      inAppSecurityFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.BiometricUnlockCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(false)
    }
  })
