package build.wallet.platform.age

import dev.zacsweers.redacted.annotations.Redacted
import kotlin.coroutines.cancellation.CancellationException

/**
 * Wraps [Apple's DeclaredAgeRange API](https://developer.apple.com/documentation/declaredagerange) (iOS 26.2+).
 *
 * Always safe to call [requestAgeRange]. Returns [AgeRangeResponse.ApiNotAvailable] when the API
 * isn't available (iOS < 26.2) or [AgeRangeResponse.NotApplicable] when user is not in an
 * applicable jurisdiction.
 *
 * **Note on naming**: Named `IosAgeRangeService` (instead of `AgeRangeService`) to avoid naming
 * collision with Apple's `DeclaredAgeRange.AgeRangeService` framework interface in Swift.
 * Using `AgeRangeService` would cause ambiguous references in Swift code.
 */
interface IosAgeRangeService {
  /**
   * Requests the user's age range from Apple's DeclaredAgeRange API.
   *
   * Returns [AgeRangeResponse.ApiNotAvailable] if the API is not available (iOS < 26.2).
   * Returns [AgeRangeResponse.NotApplicable] if user is not in an applicable jurisdiction.
   */
  @Throws(AgeRangeServiceException::class, CancellationException::class)
  suspend fun requestAgeRange(ageGate: Int): AgeRangeResponse
}

/**
 * Response from Apple's age range request.
 */
sealed interface AgeRangeResponse {
  /**
   * User shared their age range.
   * @property lowerBound Minimum age in the user's declared range (e.g., 18 means "18 or older")
   */
  @Redacted
  data class Sharing(val lowerBound: Int?) : AgeRangeResponse

  /**
   * User declined to share their age range.
   */
  data object DeclinedSharing : AgeRangeResponse

  /**
   * Age range API is not available (iOS < 26 or not supported on device).
   */
  data object ApiNotAvailable : AgeRangeResponse

  /**
   * Age verification is not applicable to this user (not in a jurisdiction requiring it).
   * Returned when `isEligibleForAgeFeatures` is false.
   */
  data object NotApplicable : AgeRangeResponse
}

/**
 * Exception thrown by [IosAgeRangeService] on API errors.
 */
class AgeRangeServiceException(
  val errorCode: String,
  override val message: String? = null,
  override val cause: Throwable? = null,
) : Exception(message, cause)
