package build.wallet.availability

import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.AgeRangeVerificationFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logError
import build.wallet.platform.age.AgeRangeResponse
import build.wallet.platform.age.AgeRangeServiceException
import build.wallet.platform.age.IosAgeRangeService
import build.wallet.platform.config.AppVariant
import kotlin.coroutines.cancellation.CancellationException

private const val ADULT_AGE = 18

@BitkeyInject(ActivityScope::class)
class AgeRangeVerificationServiceImpl(
  private val ageRangeService: IosAgeRangeService,
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

    return try {
      // Shows iOS system UI asking user to share their age range; user can share or decline
      val response = ageRangeService.requestAgeRange(ADULT_AGE)
      when (response) {
        is AgeRangeResponse.Sharing -> {
          val lowerBound = response.lowerBound
          when {
            // Null means age unknown - fail-open
            lowerBound == null -> AgeRangeVerificationResult.Allowed
            // User is 18+ - allowed
            lowerBound >= ADULT_AGE -> AgeRangeVerificationResult.Allowed
            // User is under 18 - denied
            else -> AgeRangeVerificationResult.Denied
          }
        }
        AgeRangeResponse.DeclinedSharing -> AgeRangeVerificationResult.Allowed
        // API not available (iOS < 26.2) - can't verify age, allow access
        AgeRangeResponse.ApiNotAvailable -> AgeRangeVerificationResult.Allowed
        // User not in applicable jurisdiction - age verification not required
        AgeRangeResponse.NotApplicable -> AgeRangeVerificationResult.Allowed
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: AgeRangeServiceException) {
      logError(throwable = e) {
        "[age_range_verification_error] error_code=${e.errorCode} error_message=${e.message}"
      }
      AgeRangeVerificationResult.Allowed // fail-open on error
    } catch (
      @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
      logError(throwable = e) {
        "[age_range_verification_error] error_code=UNEXPECTED error_message=${e.message}"
      }
      AgeRangeVerificationResult.Allowed // fail-open on unexpected error
    }
  }
}
