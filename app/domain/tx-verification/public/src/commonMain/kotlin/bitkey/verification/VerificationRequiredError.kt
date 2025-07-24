package bitkey.verification

/**
 * Error thrown when a grant cannot be signed until verification is performed.
 */
object VerificationRequiredError : Error("Verification is required for this transaction")
