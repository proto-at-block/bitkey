package build.wallet.availability

import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.AgeRangeVerificationFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.platform.config.AppVariant

/**
 * JVM implementation of [AgeRangeVerificationService] for integration testing.
 *
 * Unlike Android/iOS which use real platform age APIs, this JVM implementation
 * provides a controllable [fakeResult] property for simulating different age
 * verification outcomes in integration tests.
 *
 * By default, returns [AgeRangeVerificationResult.Allowed].
 */
@BitkeyInject(ActivityScope::class)
class AgeRangeVerificationServiceImpl(
  private val featureFlag: AgeRangeVerificationFeatureFlag,
  private val appVariant: AppVariant,
) : AgeRangeVerificationService {
  /**
   * The fake result to return. Set this in tests to simulate different outcomes.
   * Defaults to [AgeRangeVerificationResult.Allowed].
   */
  var fakeResult: AgeRangeVerificationResult = AgeRangeVerificationResult.Allowed

  override suspend fun verifyAgeRange(): AgeRangeVerificationResult {
    // EEK bypass - emergency access should not be blocked
    if (appVariant == AppVariant.Emergency) {
      return AgeRangeVerificationResult.Allowed
    }

    // Feature flag bypass
    if (!featureFlag.isEnabled()) {
      return AgeRangeVerificationResult.Allowed
    }

    return fakeResult
  }

  fun reset() {
    fakeResult = AgeRangeVerificationResult.Allowed
  }
}
