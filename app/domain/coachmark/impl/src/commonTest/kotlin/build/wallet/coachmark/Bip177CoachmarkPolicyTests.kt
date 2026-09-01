package build.wallet.coachmark

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
    val bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake()
    val bip177CoachmarkEligibilityDao = Bip177CoachmarkEligibilityDaoFake()
    val onboardingCompletionService = OnboardingCompletionServiceFake()
    val clock = ClockFake()

    val policy = Bip177CoachmarkPolicy(
      clock = clock,
      bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
      bip177CoachmarkEligibilityDao = bip177CoachmarkEligibilityDao,
      onboardingCompletionService = onboardingCompletionService
    )

    beforeTest {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      bip177CoachmarkEligibilityDao.reset()
      onboardingCompletionService.reset()
      clock.reset()
    }

    test("user with Satoshi is eligible for creation") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi

      policy.shouldCreate().shouldBe(true)
    }

    test("user with BTC is NOT eligible for creation") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Bitcoin

      policy.shouldCreate().shouldBe(false)
    }

    test("user who switches to Satoshi AFTER first eligibility check is NOT eligible") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Bitcoin
      policy.shouldCreate().shouldBe(false)

      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi

      policy.shouldCreate().shouldBe(false)
    }

    test("eligible user who switches away from Satoshi should not see coachmark") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      policy.shouldCreate().shouldBe(true)

      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Bitcoin

      policy.shouldShow().shouldBe(false)
    }

    test("eligible user who switches back to Satoshi should see coachmark again") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      policy.shouldCreate().shouldBe(true)

      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Bitcoin
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi

      policy.shouldShow().shouldBe(true)
    }

    test("shouldShow returns false when eligibility not yet captured") {
      // Don't call shouldCreate() - eligibility remains null

      policy.shouldShow().shouldBe(false)
    }

    test("eligibility persists across policy instances") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Bitcoin
      policy.shouldCreate()

      val newPolicy = Bip177CoachmarkPolicy(
        clock = clock,
        bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
        bip177CoachmarkEligibilityDao = bip177CoachmarkEligibilityDao,
        onboardingCompletionService = onboardingCompletionService
      )

      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      newPolicy.shouldCreate().shouldBe(false)
    }

    test("eligible user remains eligible across policy instances and can still see coachmark") {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
      // First check stores eligibility=true
      policy.shouldCreate().shouldBe(true)

      val newPolicy = Bip177CoachmarkPolicy(
        clock = clock,
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
        onboardingCompletionService.getCompletionTimestampResult = Ok(clock.now() - 59.seconds)

        policy.shouldCreate().shouldBe(false)
      }

      test("user over threshold should see coachmark") {
        bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
        onboardingCompletionService.getCompletionTimestampResult = Ok(clock.now() - 2.minutes)

        policy.shouldCreate().shouldBe(true)
      }

      test("user with no onboarding timestamp should proceed with eligibility check") {
        bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
        onboardingCompletionService.getCompletionTimestampResult = Ok(null)

        policy.shouldCreate().shouldBe(true)
      }

      test("new user remains ineligible even after threshold passes") {
        // User completes onboarding and is detected as new
        bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
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
