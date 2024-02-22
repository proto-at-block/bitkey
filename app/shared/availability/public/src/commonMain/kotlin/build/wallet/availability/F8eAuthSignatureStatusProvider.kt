package build.wallet.availability

import kotlinx.coroutines.flow.StateFlow

/**
 * Provider of the current [AuthSignatureStatus] of f8e authentication.
 */
interface F8eAuthSignatureStatusProvider {
  /**
   * Flow of the current [AuthSignatureStatus] of f8e authentication.
   *
   * When the status is [AuthSignatureStatus.Authenticated], the user is authenticated to f8e.
   * When the status is [AuthSignatureStatus.Unauthenticated], the user is not authenticated to f8e.
   */
  fun authSignatureStatus(): StateFlow<AuthSignatureStatus>

  /**
   * Updates the [AuthSignatureStatus] of f8e authentication.
   */
  suspend fun updateAuthSignatureStatus(authSignatureStatus: AuthSignatureStatus)
}

/**
 * The current status of f8e authentication.
 */
sealed interface AuthSignatureStatus {
  /** Authenticated and able to use auth f8e endpoints */
  data object Authenticated : AuthSignatureStatus

  /**
   * Unauthenticated and unable to use auth f8e endpoints.
   *
   * *Note: This is signaled when the AppAuthKey is no longer valid and f8e is no longer
   * providing valid auth tokens
   */
  data object Unauthenticated : AuthSignatureStatus
}
