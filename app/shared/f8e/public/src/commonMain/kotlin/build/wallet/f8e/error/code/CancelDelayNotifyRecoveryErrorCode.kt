package build.wallet.f8e.error.code

import kotlinx.serialization.Serializable

/**
 * Specific errors encountered when canceling a recovery
 */
@Serializable
enum class CancelDelayNotifyRecoveryErrorCode : F8eClientErrorCode {
  /** Indicates additional verification via notification comms is required to cancel the recovery */
  COMMS_VERIFICATION_REQUIRED,

  /** Indicates there is no recovery on the server to cancel */
  NO_RECOVERY_EXISTS,
}
