package build.wallet.coachmark

import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.Bip177FeatureFlag
import build.wallet.feature.flags.PrivateWalletMigrationFeatureFlag
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryFake
import build.wallet.onboarding.OnboardingCompletionServiceFake
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class CoachmarkVisibilityDeciderTests :
  FunSpec({
    val clock = ClockFake()
    val featureFlagDao = FeatureFlagDaoMock()
    val privateWalletMigrationFeatureFlag = PrivateWalletMigrationFeatureFlag(featureFlagDao)
    val bip177CoachmarkPolicy = Bip177CoachmarkPolicy(
      clock = clock,
      bip177FeatureFlag = Bip177FeatureFlag(featureFlagDao),
      bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake(),
      bip177CoachmarkEligibilityDao = Bip177CoachmarkEligibilityDaoFake(),
      onboardingCompletionService = OnboardingCompletionServiceFake()
    )

    val coachmarkVisibilityDecider = CoachmarkVisibilityDecider(
      clock = clock,
      bip177CoachmarkPolicy = bip177CoachmarkPolicy,
      privateWalletMigrationFeatureFlag = privateWalletMigrationFeatureFlag
    )

    beforeTest {
      privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    }

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

    test("PrivateWalletHomeCoachmark should be created when feature flag is enabled") {
      privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      coachmarkVisibilityDecider.shouldCreate(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
        .shouldBe(true)
    }

    test("PrivateWalletHomeCoachmark should not be created when feature flag is disabled") {
      privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      coachmarkVisibilityDecider.shouldCreate(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
        .shouldBe(false)
    }

    test("PrivateWalletHomeCoachmark should not be shown when feature flag is disabled") {
      privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.PrivateWalletHomeCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(false)
    }
  })
