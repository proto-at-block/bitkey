package build.wallet.coachmark

import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class CoachmarkVisibilityDeciderTests :
  FunSpec({

    val featureFlagDao = FeatureFlagDaoMock()
    val coachmarkVisibilityDecider = CoachmarkVisibilityDecider(
      ClockFake()
    )

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
  })
