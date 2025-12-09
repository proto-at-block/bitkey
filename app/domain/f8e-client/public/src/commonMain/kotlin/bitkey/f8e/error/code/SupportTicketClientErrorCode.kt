package bitkey.f8e.error.code

import kotlinx.serialization.Serializable

/**
 * Specific errors encountered when creating a support ticket.
 */
@Serializable
enum class SupportTicketClientErrorCode : F8eClientErrorCode {
  /** Indicates that the email address entered is invalid. */
  INVALID_EMAIL_ADDRESS,
}
