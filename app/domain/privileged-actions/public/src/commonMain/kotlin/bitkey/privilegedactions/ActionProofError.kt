package bitkey.privilegedactions

/**
 * Errors that can occur when computing action proofs.
 */
sealed class ActionProofError : Error() {
  /**
   * No active account or auth token available.
   */
  data object NoAuthToken : ActionProofError() {
    override val message: String = "No active account or auth token available"
  }

  /**
   * An underlying operation failed (token retrieval or FFI call).
   */
  data class InternalError(override val cause: Throwable) : ActionProofError() {
    override val message: String? = cause.message
  }

  /**
   * Invalid signature format.
   */
  data class InvalidSignature(val reason: String) : ActionProofError() {
    override val message: String = reason
  }

  /**
   * Invalid context bindings provided.
   */
  data class InvalidBindings(val reason: String) : ActionProofError() {
    override val message: String = reason
  }
}
