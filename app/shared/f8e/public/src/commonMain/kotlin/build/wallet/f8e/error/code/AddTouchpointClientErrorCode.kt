package build.wallet.f8e.error.code

import kotlinx.serialization.Serializable

/**
 * Specific errors encountered when adding a notification touchpoint
 */
@Serializable
enum class AddTouchpointClientErrorCode : F8eClientErrorCode {
  /** Indicates the SMS touchpoint attempting to be added belongs to a country we do not support. */
  UNSUPPORTED_COUNTRY_CODE,

  /** Indicates the SMS touchpoint attempting to be added is already active on the account. */
  TOUCHPOINT_ALREADY_ACTIVE,

  /** Indicates that the email address entered is invalid. */
  INVALID_EMAIL_ADDRESS,
}
