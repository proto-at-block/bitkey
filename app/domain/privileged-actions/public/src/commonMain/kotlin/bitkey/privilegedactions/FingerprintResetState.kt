package bitkey.privilegedactions

import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import build.wallet.grants.Grant

/**
 * Represents the different states of a fingerprint reset operation.
 */
sealed interface FingerprintResetState {
  val isReady: Boolean

  /** No reset operation is available */
  object None : FingerprintResetState {
    override val isReady = false
  }

  /** A persisted grant is available and ready to be used */
  data class GrantReady(
    val grant: Grant,
  ) : FingerprintResetState {
    override val isReady = true
  }

  /** A server-side action has completed its delay period and is ready */
  data class DelayCompleted(
    val action: PrivilegedActionInstance,
  ) : FingerprintResetState {
    override val isReady = true
  }

  /** A server-side action exists but is still in its delay period */
  data class DelayInProgress(
    val action: PrivilegedActionInstance,
    val delayAndNotify: AuthorizationStrategy.DelayAndNotify,
  ) : FingerprintResetState {
    override val isReady = false
  }
}
