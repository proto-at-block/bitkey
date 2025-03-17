package bitkey.f8e.error.code

import kotlinx.serialization.Serializable

/**
 * Error codes used in Mobile Pay endpoints.
 */
@Serializable
enum class MobilePayErrorCode : F8eClientErrorCode {
  /** Indicates the user does not have a Mobile Pay limit set **/
  NO_SPENDING_LIMIT_EXISTS,
}
