package build.wallet.wallet.migration

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Sek
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Service for managing private wallet migration from legacy multisig to private collaborative custody.
 */
interface PrivateWalletMigrationService {
  /**
   * The current state of the private wallet migration.
   *
   * Use this flag to check if the user has a private wallet migration
   * available, as well as whether they have started a migration
   * already that needs to be resumed.
   */
  val migrationState: Flow<PrivateWalletMigrationState>

  /**
   * Initiates the private wallet migration process.
   *
   * @param account The full account to migrate
   * @param proofOfPossession Proof of possession needed to authorize the key creation process
   * @param newHwKeys The newly generated hardware keys to migrate to.
   * @return Result containing the updated keybox with both old and new keysets, or error
   */
  suspend fun initiateMigration(
    account: FullAccount,
    proofOfPossession: HwFactorProofOfPossession,
    newHwKeys: HwKeyBundle,
    ssek: Sek,
    sealedSsek: SealedSsek,
  ): Result<PrivateWalletMigrationState.InitiationComplete, PrivateWalletMigrationError>

  /**
   * Estimates the network fees for the migration sweep transaction.
   * This can be called before creating the new keyset to show the user
   * an estimate of the network fees they will pay.
   *
   * @param account The account being migrated
   * @return Result containing the estimated fee or error
   */
  suspend fun estimateMigrationFees(
    account: FullAccount,
  ): Result<BitcoinMoney, PrivateWalletMigrationError>

  /**
   * Save the state of the migration with a cloud backup marked as complete.
   */
  suspend fun completeCloudBackup(): Result<Unit, PrivateWalletMigrationError>

  /**
   * Completes the migration by clearing any local state
   */
  suspend fun completeMigration(): Result<Unit, PrivateWalletMigrationError>

  /**
   * Clears any local migration state. Used when deleting app data via the debug menu.
   */
  suspend fun clearMigration()
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
   * Cloud backup failed after keyset creation.
   */
  data class CloudBackupFailed(val error: Throwable) : PrivateWalletMigrationError

  /**
   * Descriptors failed to backup to F8e.
   */
  data class DescriptorBackupFailed(val error: Throwable) : PrivateWalletMigrationError

  /**
   * Failed to activate the new private keyset on the server.
   */
  data class KeysetServerActivationFailed(val error: Throwable) : PrivateWalletMigrationError

  /**
   * Failed to activate the new private keyset.
   */
  data class KeysetActivationFailed(val error: Throwable) : PrivateWalletMigrationError

  /**
   * Failed to estimate migration fees.
   */
  data class FeeEstimationFailed(val error: Throwable) : PrivateWalletMigrationError

  /**
   * Wallet balance is less than the network fees required for migration.
   */
  data object InsufficientFundsForMigration : PrivateWalletMigrationError

  /**
   * Failed to complete the migration
   */
  data class MigrationCompletionFailed(val error: Throwable) : PrivateWalletMigrationError

  /**
   * Unknown error occurred.
   */
  data class UnknownError(val error: Throwable) : PrivateWalletMigrationError
}
