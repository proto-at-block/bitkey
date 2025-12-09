package bitkey.privilegedactions

/**
 * Errors that can occur during privileged action operations
 */
sealed class PrivilegedActionError {
  /**
   * Error when the server request fails
   */
  data class ServerError(val cause: Throwable) : PrivilegedActionError()

  /**
   * Error when creating the request for a privileged action fails
   */
  data class InvalidRequest(val cause: Throwable) : PrivilegedActionError()

  /**
   * Error when the server response is invalid or unexpected
   */
  data class InvalidResponse(val cause: Throwable) : PrivilegedActionError()

  /**
   * Error when we couldn't fetch a FullAccount
   */
  data object IncorrectAccountType : PrivilegedActionError()

  /**
   * Error when the action is not authorized yet (delay period not completed)
   */
  data object NotAuthorized : PrivilegedActionError()

  /**
   * Error when the action type is not supported
   */
  data object UnsupportedActionType : PrivilegedActionError()

  /**
   * Error when multiple pending actions of the expected type are found, but only one was expected.
   */
  data object MultiplePendingActionsFound : PrivilegedActionError()

  /**
   * Error when a database operation fails
   */
  data class DatabaseError(val cause: Throwable) : PrivilegedActionError()
}
