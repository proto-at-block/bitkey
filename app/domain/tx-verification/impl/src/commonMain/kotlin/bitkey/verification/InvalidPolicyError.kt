package bitkey.verification

/**
 * Unexpected error returned when policy cannot be validly constructed from raw data.
 */
class InvalidPolicyError(
  message: String,
) : Error(message)
