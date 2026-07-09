package build.wallet.wallet.migration

/**
 * Errors that can occur during the migration process.
 */
sealed interface MigrationError {
  /** Failed to retrieve the current keybox. */
  data class KeyboxNotFound(val cause: Throwable? = null) : MigrationError

  /** Failed to generate app keys. */
  data class AppKeyGenerationFailed(val cause: Throwable) : MigrationError

  /** Failed to create keyset on server. */
  data class ServerKeysetCreationFailed(val cause: Throwable) : MigrationError

  /** Failed to activate the local keybox. */
  data class LocalKeyboxActivationFailed(val cause: Throwable) : MigrationError

  /** Failed to backup descriptors. */
  data class DescriptorBackupFailed(val cause: Throwable) : MigrationError

  /** Failed to activate server keyset. */
  data class ServerKeysetActivationFailed(val cause: Throwable) : MigrationError

  /** Failed to complete cloud backup. */
  data class CloudBackupFailed(val cause: Throwable) : MigrationError

  /** Failed to rotate auth keys on the server or locally. */
  data class AuthKeyRotationFailed(val cause: Throwable) : MigrationError

  /** The paired W3 hardware auth key is already in use by another account or recovery. */
  data class HardwareAuthKeyAlreadyInUse(val cause: Throwable? = null) : MigrationError

  /** Failed to check whether the paired W3 hardware auth key is usable due to a transient or unknown error. */
  data class HardwareAuthKeyAvailabilityCheckFailed(val cause: Throwable) : MigrationError

  /** Failed to re-seal and upload the DDK with new hardware. */
  data class DdkBackupFailed(val cause: Throwable) : MigrationError

  data class MigrationCompletionFailed(val cause: Throwable) : MigrationError

  /** Invalid state for the requested operation. */
  data class InvalidState(val message: String) : MigrationError

  /** Failed to persist migration state. */
  data class StatePersistenceFailed(val cause: Throwable) : MigrationError

  /** Missing required context for the operation. */
  sealed interface MissingContext : MigrationError {
    val message: String

    /** Generic missing context with a descriptive message. */
    data class Generic(override val message: String) : MissingContext

    /** Auth key rotation requires the old W1 hardware proof of possession. */
    data object W3AuthRotationOldHardwareProof : MissingContext {
      override val message: String = "W3 auth key rotation requires old W1 proof of possession."
    }

    /** Resumed W3 auth key rotation requires a fresh W3 action proof. */
    data object W3AuthRotationNewHardwareActionProof : MissingContext {
      override val message: String =
        "Resumed W3 auth key rotation requires a fresh W3 action proof."
    }
  }

  /** Failed to estimate migration fees. */
  data class FeeEstimationFailed(val cause: Throwable) : MigrationError

  /** Wallet balance is less than the network fees required for migration. */
  data object InsufficientFundsForMigration : MigrationError
}
