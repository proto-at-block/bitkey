package build.wallet.wallet.migration

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.recovery.sweep.Sweep
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Service for managing private wallet migration from legacy multisig to private collaborative custody.
 */
interface PrivateWalletMigrationService {
  /**
   * Determines if the private wallet migration feature is available to the user.
   * Checks if the feature flag is enabled and if the user needs migration.
   *
   * @return True if the migration feature is available, false otherwise
   */
  val isPrivateWalletMigrationAvailable: Flow<Boolean>

  /**
   * Initiates the private wallet migration process.
   *
   * @param account The full account to migrate
   * @param proofOfPossession Proof of possession needed to authorize the key creation process
   * @param newHwKeys The newly generated hardware keys to migrate to.
   * @return Result indicating successful initiation or error
   */
  suspend fun initiateMigration(
    account: FullAccount,
    proofOfPossession: HwFactorProofOfPossession,
    newHwKeys: HwKeyBundle,
  ): Result<SpendingKeyset, PrivateWalletMigrationError>

  /**
   * Generates the sweep transaction from old keyset to new private keyset.
   * Uses chaincode delegation for privacy-preserving signatures.
   *
   * @param account The account being migrated
   * @return Result containing the prepared sweep or error
   */
  suspend fun prepareSweep(
    account: FullAccount,
  ): Result<PrivateWalletSweep?, PrivateWalletMigrationError>

  /**
   * Finalizes the migration after successful sweep.
   * This atomically activates the new private keyset and cleans up old descriptors.
   *
   * @param sweepTxId Transaction ID of the completed sweep
   * @param account The account being migrated
   * @return Result indicating successful completion or error
   */
  suspend fun finalizeMigration(
    sweepTxId: String,
    account: FullAccount,
  ): Result<Keybox, PrivateWalletMigrationError>

  /**
   * Cancels an in-progress migration.
   * Cleans up any temporary state and reverts to original keyset.
   *
   * @param account The account with migration in progress
   * @return Result indicating successful cancellation or error
   */
  suspend fun cancelMigration(account: FullAccount): Result<Unit, PrivateWalletMigrationError>
}

/**
 * Errors that can occur during private wallet migration.
 */
sealed interface PrivateWalletMigrationError {
  /**
   * Feature is not available (feature flag disabled or account not eligible).
   */
  data object FeatureNotAvailable : PrivateWalletMigrationError

  /**
   * Network connectivity issues preventing migration.
   */
  data object NetworkUnavailable : PrivateWalletMigrationError

  /**
   * Failed to create new private keyset.
   */
  data class KeysetCreationFailed(val error: Throwable) : PrivateWalletMigrationError

  /**
   * Failed to prepare or sign sweep transaction.
   */
  data class SweepPreparationFailed(val error: Throwable) : PrivateWalletMigrationError

  /**
   * Sweep transaction broadcast failed.
   */
  data class SweepBroadcastFailed(val error: Throwable) : PrivateWalletMigrationError

  /**
   * Migration finalization failed.
   */
  data class FinalizationFailed(val error: Throwable) : PrivateWalletMigrationError

  /**
   * General server error during migration.
   */
  data class ServerError(val error: Throwable) : PrivateWalletMigrationError

  /**
   * Unknown error occurred.
   */
  data class UnknownError(val error: Throwable) : PrivateWalletMigrationError
}

/**
 * Sweep data for private wallet migration.
 * Contains the prepared transaction for moving funds to new private keyset.
 */
data class PrivateWalletSweep(
  val sweep: Sweep,
  val oldKeysetId: String,
  val newKeysetId: String,
  val estimatedConfirmationTime: Instant?,
)
