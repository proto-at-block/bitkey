package build.wallet.f8e.error.code

import kotlinx.serialization.Serializable

/**
 * Specific errors encountered when initiating a recovery
 */
@Serializable
enum class InitiateAccountDelayNotifyErrorCode : F8eClientErrorCode {
  /** Indicates additional verification via notification comms is required to initiate a recovery */
  COMMS_VERIFICATION_REQUIRED,

  /** Indicates there is already an active recovery on the account so a new one cannot be initiated */
  RECOVERY_ALREADY_EXISTS,
}
