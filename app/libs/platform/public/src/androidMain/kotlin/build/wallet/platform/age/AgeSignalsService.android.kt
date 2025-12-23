package build.wallet.platform.age

/**
 * Wraps [Google Play Age Signals API](https://developer.android.com/google/play/age-signals).
 */
interface AgeSignalsService {
  /**
   * Queries Google Play for the user's age verification status.
   *
   * Returns immediately - no user interaction required. Google determines the status based on
   * the user's Google account settings and Play Store supervision status.
   *
   * @return See [AgeSignalsResponse] for possible states.
   */
  suspend fun checkAgeSignals(): AgeSignalsResponse
}

/**
 * Response from Play Age Signals API.
 */
sealed interface AgeSignalsResponse {
  /**
   * User is verified as 18 or older.
   */
  data object Verified : AgeSignalsResponse

  /**
   * User has a supervised Google Account (SUPERVISED, SUPERVISED_APPROVAL_PENDING, or SUPERVISED_APPROVAL_DENIED).
   */
  data object Supervised : AgeSignalsResponse

  /**
   * User is in applicable jurisdiction but not verified or supervised.
   */
  data object Unknown : AgeSignalsResponse

  /**
   * User is not in applicable jurisdiction or status unavailable.
   */
  data object NotApplicable : AgeSignalsResponse

  /**
   * API error occurred.
   */
  data class Error(
    val errorCode: String,
    override val message: String? = null,
    override val cause: Throwable? = null,
  ) : AgeSignalsResponse, Throwable(message, cause)
}
