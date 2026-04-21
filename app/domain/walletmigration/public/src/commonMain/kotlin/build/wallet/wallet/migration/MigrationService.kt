package build.wallet.wallet.migration

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Result

/**
 * Service for managing wallet migration using a state machine approach.
 *
 * The migration process follows a well-defined sequence of states (see [MigrationProgress])
 * that can be resumed and advanced step-by-step.
 */
interface MigrationService {
  /**
   * Resume an in-progress migration, returning the current state.
   *
   * This should be called when the app starts to check if there is a migration
   * in progress that needs to be completed.
   *
   * Note: This returns a "partial" state that indicates what step the migration is at.
   * For states that require ephemeral data (hwProofOfPossession, sealedSsek), the caller
   * must use the state's transition methods to create a complete state with that data
   * before calling [proceed].
   *
   * @param type The type of migration to resume
   * @return The current migration progress state
   */
  suspend fun resume(type: MigrationType): Result<MigrationProgress, MigrationError>

  /**
   * Advance the migration to the next state by executing the domain logic for the current state.
   *
   * All required data is obtained from the state object itself, which carries the
   * necessary context (hardware keys, proofs, encryption keys) through the flow.
   *
   * @param state The current migration progress state
   * @return The next migration progress state after executing the current step
   */
  suspend fun proceed(state: MigrationProgress): Result<MigrationProgress, MigrationError>

  /**
   * Estimates the network fees for the migration sweep transaction.
   * This can be called before starting migration to show the user
   * an estimate of the network fees they will pay.
   *
   * @param account The account being migrated
   * @return Result containing the estimated fee or error
   */
  suspend fun estimateMigrationFees(
    account: FullAccount,
    oldHardwareFingerprint: String? = null,
  ): Result<BitcoinMoney, MigrationError>

  /**
   * Clears any local migration state for the given type.
   * Used when deleting app data via the debug menu.
   */
  suspend fun clearMigration(type: MigrationType)

  /**
   * Checks whether the HW auth key is unknown to F8e (404).
   *
   * Cloud restore combines this with a failed recovery-app-auth check to determine whether
   * it is resuming an interrupted W3 upgrade. Best-effort: returns false on any non-404 failure.
   */
  suspend fun isW3UpgradeInProgress(
    f8eEnvironment: F8eEnvironment,
    hwAuthPublicKey: HwAuthPublicKey,
  ): Boolean

  /**
   * Returns the persisted old hardware fingerprint for a W3 upgrade, or null if not yet saved.
   */
  suspend fun getOldHardwareFingerprint(): Result<String?, MigrationError>
}
