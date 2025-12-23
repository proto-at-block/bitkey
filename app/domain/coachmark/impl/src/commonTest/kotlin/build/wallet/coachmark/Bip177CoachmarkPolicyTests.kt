package build.wallet.coachmark

import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.flags.Bip177FeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryFake
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.onboarding.OnboardingCompletionServiceFake
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class Bip177CoachmarkPolicyTests :
  FunSpec({
    val featureFlagDao = FeatureFlagDaoMock()
    val bip177FeatureFlag = Bip177FeatureFlag(featureFlagDao)
    val bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake()
    val bip177CoachmarkEligibilityDao = Bip177CoachmarkEligibilityDaoFake()
    val onboardingCompletionService = OnboardingCompletionServiceFake()
    val clock = ClockFake()

    val policy = Bip177CoachmarkPolicy(
      clock = clock,
      bip177FeatureFlag = bip177FeatureFlag,
      bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
      bip177CoachmarkEligibilityDao = bip177CoachmarkEligibilityDao,
      onboardingCompletionService = onboardingCompletionService
    )

    beforeTest {
      bip177FeatureFlag.setFlagValue(false)
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      bip177CoachmarkEligibilityDao.reset()
      onboardingCompletionService.reset()
      clock.reset()
    }

    test("user with Satoshi when flag enabled is eligible for creation") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      bip177FeatureFlag.setFlagValue(true)

      policy.shouldCreate().shouldBe(true)
    }

    test("user with BTC when flag enabled is NOT eligible for creation") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Bitcoin
      bip177FeatureFlag.setFlagValue(true)

      policy.shouldCreate().shouldBe(false)
    }

    test("user who switches to Satoshi AFTER flag enabled is NOT eligible") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Bitcoin
      bip177FeatureFlag.setFlagValue(true)
      policy.shouldCreate().shouldBe(false)

      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi

      policy.shouldCreate().shouldBe(false)
    }

    test("eligible user who switches away from Satoshi should not see coachmark") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      bip177FeatureFlag.setFlagValue(true)
      policy.shouldCreate().shouldBe(true)

      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Bitcoin

      policy.shouldShow().shouldBe(false)
    }

    test("eligible user who switches back to Satoshi should see coachmark again") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      bip177FeatureFlag.setFlagValue(true)
      policy.shouldCreate().shouldBe(true)

      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Bitcoin
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi

      policy.shouldShow().shouldBe(true)
    }

    test("shouldCreate returns false when flag off") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      bip177FeatureFlag.setFlagValue(false)

      policy.shouldCreate().shouldBe(false)
    }

    test("shouldShow returns false when flag off") {
      policy.shouldShow().shouldBe(false)
    }

    test("shouldShow returns false when eligibility not yet captured") {
      bip177FeatureFlag.setFlagValue(true)
      // Don't call shouldCreate() - eligibility remains null

      policy.shouldShow().shouldBe(false)
    }

    test("BIP177 coachmark hidden when flag off even if eligible") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      bip177FeatureFlag.setFlagValue(true)
      policy.shouldCreate()

      bip177FeatureFlag.setFlagValue(false)

      policy.shouldShow().shouldBe(false)
    }

    test("eligibility persists across policy instances") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Bitcoin
      bip177FeatureFlag.setFlagValue(true)
      policy.shouldCreate()

      val newPolicy = Bip177CoachmarkPolicy(
        clock = clock,
        bip177FeatureFlag = bip177FeatureFlag,
        bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
        bip177CoachmarkEligibilityDao = bip177CoachmarkEligibilityDao,
        onboardingCompletionService = onboardingCompletionService
      )

      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      newPolicy.shouldCreate().shouldBe(false)
    }

    test("eligible user remains eligible across policy instances and can still see coachmark") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      bip177FeatureFlag.setFlagValue(true)
      // First check stores eligibility=true
      policy.shouldCreate().shouldBe(true)

      val newPolicy = Bip177CoachmarkPolicy(
        clock = clock,
        bip177FeatureFlag = bip177FeatureFlag,
        bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
        bip177CoachmarkEligibilityDao = bip177CoachmarkEligibilityDao,
        onboardingCompletionService = onboardingCompletionService
      )

      // Still on sats, should continue to be eligible and show
      newPolicy.shouldCreate().shouldBe(true)
      newPolicy.shouldShow().shouldBe(true)
    }

    context("New user detection") {
      test("user under threshold should NOT see coachmark") {
        bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
        bip177FeatureFlag.setFlagValue(true)
        onboardingCompletionService.getCompletionTimestampResult = Ok(clock.now() - 59.seconds)

        policy.shouldCreate().shouldBe(false)
      }

      test("user over threshold should see coachmark") {
        bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
        bip177FeatureFlag.setFlagValue(true)
        onboardingCompletionService.getCompletionTimestampResult = Ok(clock.now() - 2.minutes)

        policy.shouldCreate().shouldBe(true)
      }

      test("user with no onboarding timestamp should proceed with eligibility check") {
        bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
        bip177FeatureFlag.setFlagValue(true)
        onboardingCompletionService.getCompletionTimestampResult = Ok(null)

        policy.shouldCreate().shouldBe(true)
      }

      test("new user remains ineligible even after threshold passes") {
        // User completes onboarding, flag is enabled, and they're detected as new
        bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
        bip177FeatureFlag.setFlagValue(true)
        onboardingCompletionService.getCompletionTimestampResult = Ok(clock.now() - 30.seconds)

        // First check - user is new, should be marked ineligible
        policy.shouldCreate().shouldBe(false)

        // Simulate time passing - user is no longer "new" by threshold
        clock.advanceBy(2.minutes)

        // User should still be ineligible because eligibility was stored as false
        policy.shouldCreate().shouldBe(false)
        policy.shouldShow().shouldBe(false)
      }
    }
  })
