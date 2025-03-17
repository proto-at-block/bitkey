package bitkey.f8e.error

import kotlinx.serialization.Serializable

/**
 * A category of errors returned by F8e for requests.
 */
@Serializable
enum class F8eClientErrorCategory {
  /** The request requires additional authentication */
  AUTHENTICATION_ERROR,

  /** There was a general issue with the request */
  INVALID_REQUEST_ERROR,
}
