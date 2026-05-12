package build.wallet.coachmark

import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.Bip177FeatureFlag
import build.wallet.feature.flags.PrivateWalletMigrationFeatureFlag
import build.wallet.feature.flags.W3UpgradeBlockerFeatureFlag
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
    val featureFlagDao = FeatureFlagDaoMock()
    val privateWalletMigrationFeatureFlag = PrivateWalletMigrationFeatureFlag(featureFlagDao)
    val w3UpgradeBlockerFeatureFlag = W3UpgradeBlockerFeatureFlag(featureFlagDao)
    val onboardingCompletionService = OnboardingCompletionServiceFake()
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
      privateWalletMigrationFeatureFlag = privateWalletMigrationFeatureFlag,
      w3UpgradeBlockerFeatureFlag = w3UpgradeBlockerFeatureFlag,
      onboardingCompletionService = onboardingCompletionService
    )

    beforeTest {
      privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      w3UpgradeBlockerFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      onboardingCompletionService.reset()
    }

    test("return unexpired coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.SecurityHubSettingsCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("return unviewed coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.SecurityHubSettingsCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_FUTURE
        )
      ).shouldBe(true)
    }

    test("return feature flag on coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.SecurityHubSettingsCoachmark,
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
          CoachmarkIdentifier.SecurityHubSettingsCoachmark,
          viewed = false,
          expiration = Instant.DISTANT_PAST
        )
      ).shouldBe(false)
    }

    test("don't return viewed coachmarks") {
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(
          CoachmarkIdentifier.SecurityHubSettingsCoachmark,
          viewed = true,
          expiration = Instant.DISTANT_PAST
        )
      ).shouldBe(false)
    }

    test("PrivateWalletHomeCoachmark should not be created regardless of feature flag") {
      privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      coachmarkVisibilityDecider.shouldCreate(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
        .shouldBe(false)
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

    test("W3UpgradeBlockerCoachmark is eligible when flag enabled and no onboarding timestamp") {
      w3UpgradeBlockerFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      coachmarkVisibilityDecider.shouldCreate(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)
        .shouldBe(true)
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(CoachmarkIdentifier.W3UpgradeBlockerCoachmark, viewed = false, expiration = null)
      ).shouldBe(true)
    }

    test("W3UpgradeBlockerCoachmark is eligible when flag enabled and 14+ days since onboarding") {
      w3UpgradeBlockerFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
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
      w3UpgradeBlockerFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      onboardingCompletionService.getCompletionTimestampResult = Ok(clock.now() - 5.days)
      coachmarkVisibilityDecider.shouldCreate(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)
        .shouldBe(false)
      // Re-set because the fake resets after first read
      onboardingCompletionService.getCompletionTimestampResult = Ok(clock.now() - 5.days)
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(CoachmarkIdentifier.W3UpgradeBlockerCoachmark, viewed = false, expiration = null)
      ).shouldBe(false)
    }

    test("W3UpgradeBlockerCoachmark is ineligible when feature flag is disabled") {
      w3UpgradeBlockerFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      coachmarkVisibilityDecider.shouldCreate(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)
        .shouldBe(false)
      coachmarkVisibilityDecider.shouldShow(
        Coachmark(CoachmarkIdentifier.W3UpgradeBlockerCoachmark, viewed = false, expiration = null)
      ).shouldBe(false)
    }
  })
