package bitkey.f8e.privilegedactions

/**
 * Result of requesting a privileged action that may or may not require further authorization.
 */
sealed interface OptionalPrivilegedAction<Res> {
  /**
   * The privileged action was completed immediately and returned a response.
   */
  data class NotRequired<Res>(
    val response: Res,
  ) : OptionalPrivilegedAction<Res>

  /**
   * The privileged action requires further authorization.
   */
  data class Required<Res>(
    val privilegedActionInstance: PrivilegedActionInstance,
  ) : OptionalPrivilegedAction<Res>
}
