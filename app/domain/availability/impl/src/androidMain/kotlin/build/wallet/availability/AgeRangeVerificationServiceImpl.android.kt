package build.wallet.availability

import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.AgeRangeVerificationFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logError
import build.wallet.platform.age.AgeSignalsResponse
import build.wallet.platform.age.AgeSignalsService
import build.wallet.platform.config.AppVariant

@BitkeyInject(ActivityScope::class)
class AgeRangeVerificationServiceImpl(
  private val ageSignalsService: AgeSignalsService,
  private val featureFlag: AgeRangeVerificationFeatureFlag,
  private val appVariant: AppVariant,
) : AgeRangeVerificationService {
  override suspend fun verifyAgeRange(): AgeRangeVerificationResult {
    // EEK bypass - emergency access should not be blocked
    if (appVariant == AppVariant.Emergency) {
      return AgeRangeVerificationResult.Allowed
    }

    // Feature flag bypass
    if (!featureFlag.isEnabled()) {
      return AgeRangeVerificationResult.Allowed
    }

    return when (val response = ageSignalsService.checkAgeSignals()) {
      AgeSignalsResponse.Verified -> AgeRangeVerificationResult.Allowed
      AgeSignalsResponse.Supervised -> AgeRangeVerificationResult.Denied
      AgeSignalsResponse.Unknown -> AgeRangeVerificationResult.Allowed // fail-open
      AgeSignalsResponse.NotApplicable -> AgeRangeVerificationResult.Allowed // not in applicable jurisdiction
      is AgeSignalsResponse.Error -> {
        logError(throwable = response) { "[age_range_verification_error] error_code=${response.errorCode}" }
        AgeRangeVerificationResult.Allowed // fail-open on error
      }
    }
  }
}
