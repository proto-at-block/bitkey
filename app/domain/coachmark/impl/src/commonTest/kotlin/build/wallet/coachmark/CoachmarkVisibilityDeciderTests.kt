package build.wallet.coachmark

import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.BalanceHistoryFeatureFlag
import build.wallet.feature.flags.SecurityHubFeatureFlag
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class CoachmarkVisibilityDeciderTests :
  FunSpec({

    val featureFlagDao = FeatureFlagDaoMock()
    val balanceHistoryFeatureFlag = BalanceHistoryFeatureFlag(featureFlagDao)
    val securityHubFeatureFlag = SecurityHubFeatureFlag(featureFlagDao)
    val coachmarkVisibilityDecider = CoachmarkVisibilityDecider(
      ClockFake(),
      balanceHistoryFeatureFlag,
      securityHubFeatureFlag
    )

    beforeTest {
      balanceHistoryFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    }

    test("return unexpired coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.InheritanceCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("return unviewed coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.InheritanceCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("return feature flag on coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.InheritanceCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("don't return expired coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.InheritanceCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_PAST
        )
      ).shouldBe(false)
    }

    test("don't return viewed coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.InheritanceCoachmark,
          viewed = true,
          expiration = Instant.DISTANT_PAST
        )
      ).shouldBe(false)
    }

    test("don't return feature flagged coachmarks that are off") {
      balanceHistoryFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.BalanceGraphCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(false)
    }
  })
