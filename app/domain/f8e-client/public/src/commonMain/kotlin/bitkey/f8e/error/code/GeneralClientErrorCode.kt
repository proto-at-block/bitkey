package bitkey.f8e.error.code

import kotlinx.serialization.Serializable

/** There was a general error with a request to F8e. */
@Serializable
enum class GeneralClientErrorCode : F8eClientErrorCode {
  BAD_REQUEST,
  CONFLICT,
  NOT_FOUND,
  FORBIDDEN,
}
