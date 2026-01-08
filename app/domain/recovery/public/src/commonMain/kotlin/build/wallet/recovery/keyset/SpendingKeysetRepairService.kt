package build.wallet.recovery.keyset

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.ListKeysetsResponse
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Service for detecting and repairing keyset mismatches from stale cloud backup recovery.
 *
 * When a user recovers from a stale cloud backup, their local `activeSpendingKeyset`
 * may not match the server's active keyset. This service:
 * 1. Detects mismatches via [syncStatus] flow (updated by worker on startup/foreground)
 * 2. Provides on-demand mismatch checking via [checkSyncStatus]
 * 3. Manages the repair process via [attemptRepair]
 *
 * The repair process is resumable - if the app crashes during repair, the mismatch
 * will still be detected and the user can retry.
 */
interface SpendingKeysetRepairService {
  /**
   * Emits the current keyset sync status.
   * Updated by worker on startup and when app returns to foreground.
   * Returns [SpendingKeysetSyncStatus.Synced] if feature flag is disabled.
   */
  val syncStatus: StateFlow<SpendingKeysetSyncStatus>

  /**
   * Checks if the account has private keysets that require SSEK unsealing.
   *
   * @param account The full account to check
   * @return [PrivateKeysetInfo.NeedsUnsealing] with the sealed SSEK if private keysets exist,
   *         [PrivateKeysetInfo.None] if no private keysets exist, or an error if the check fails
   */
  suspend fun checkPrivateKeysets(
    account: FullAccount,
  ): Result<PrivateKeysetInfo, KeysetRepairError>

  /**
   * Initiates repair process.
   *
   * @param account The full account to repair
   * @param cachedData Cached response data from [checkPrivateKeysets] to avoid duplicate network calls.
   * @param sealedSsek Sealed secret storage encryption key for decrypting private keysets.
   *                   Required if [checkPrivateKeysets] returns [PrivateKeysetInfo.NeedsUnsealing].
   */
  suspend fun attemptRepair(
    account: FullAccount,
    cachedData: KeysetRepairCachedData,
  ): Result<KeysetRepairState.RepairComplete, KeysetRepairError>

  /**
   * Regenerates the active keyset when the app spending private key is missing.
   *
   * This method:
   * 1. Generates a new app spending key pair
   * 2. Creates a new keyset on the server with the provided hardware key
   * 3. Saves the keybox locally with the new active keyset
   * 4. Uploads descriptor backup (if sealed SSEK is available)
   * 5. Activates the keyset on the server
   * 6. Creates and uploads a cloud backup
   *
   * @param account The full account to repair
   * @param updatedKeybox The keybox that was being updated when the missing key error occurred
   * @param hwSpendingKey The new hardware spending key obtained via NFC
   * @param hwProofOfPossession Proof of possession from the hardware device
   * @param cachedData Cached response data containing the sealed SSEK for descriptor backup
   */
  suspend fun regenerateActiveKeyset(
    account: FullAccount,
    updatedKeybox: Keybox,
    hwSpendingKey: HwSpendingPublicKey,
    hwProofOfPossession: HwFactorProofOfPossession,
    cachedData: KeysetRepairCachedData,
  ): Result<KeysetRepairState.RepairComplete, KeysetRepairError>
}

/**
 * Represents the sync status between local and server keysets.
 */
sealed interface SpendingKeysetSyncStatus {
  /**
   * Local and server keysets are in sync.
   */
  data object Synced : SpendingKeysetSyncStatus

  /**
   * Local active keyset doesn't match server's active keyset.
   */
  data class Mismatch(
    val localActiveKeysetId: String,
    val serverActiveKeysetId: String,
  ) : SpendingKeysetSyncStatus

  /**
   * Unable to determine sync status (e.g., network error).
   */
  data class Unknown(val error: Throwable) : SpendingKeysetSyncStatus
}

/**
 * Represents the current state of the keyset repair process.
 * State is derived from persisted data + account state, making it resumable.
 */
sealed interface KeysetRepairState {
  /**
   * Feature flag disabled or no mismatch detected - repair not needed.
   */
  data object NotNeeded : KeysetRepairState

  /**
   * Mismatch detected, repair available but not started.
   */
  data class Available(
    val localActiveKeysetId: String,
    val serverActiveKeysetId: String,
  ) : KeysetRepairState

  /**
   * All steps complete.
   */
  data class RepairComplete(
    val updatedKeybox: Keybox,
  ) : KeysetRepairState
}

/**
 * Information about whether private keysets exist and require SSEK unsealing.
 * Also contains cached data from the f8e response to avoid duplicate network calls.
 */
sealed interface PrivateKeysetInfo {
  /**
   * No private keysets exist - SSEK unsealing is not needed.
   * Contains cached response data to pass to [SpendingKeysetRepairService.attemptRepair].
   */
  data class None(
    val cachedResponseData: KeysetRepairCachedData,
  ) : PrivateKeysetInfo

  /**
   * Private keysets exist and require SSEK unsealing.

   * @param cachedResponseData Cached response data to pass to [SpendingKeysetRepairService.attemptRepair].
   */
  data class NeedsUnsealing(
    val cachedResponseData: KeysetRepairCachedData,
  ) : PrivateKeysetInfo
}

/**
 * Cached data from the f8e ListKeysets response.
 * Should be passed to [SpendingKeysetRepairService.attemptRepair]
 * to avoid duplicate network calls.
 */
data class KeysetRepairCachedData(
  /** Response from ListKeysets API containing all keysets, descriptor backups, and activeKeysetId. */
  val response: ListKeysetsResponse,
  /** The server's active keyset ID (convenience accessor for response.activeKeysetId). */
  val serverActiveKeysetId: String,
)

/**
 * Errors that can occur during keyset repair.
 */
sealed interface KeysetRepairError {
  val message: String
  val cause: Throwable

  data class FetchKeysetsFailed(
    override val message: String = "Failed to fetch keysets from server",
    override val cause: Throwable,
  ) : KeysetRepairError

  data class DecryptKeysetsFailed(
    override val message: String = "Failed to decrypt keysets",
    override val cause: Throwable,
  ) : KeysetRepairError

  data class SaveKeyboxFailed(
    override val message: String = "Failed to save keybox",
    override val cause: Throwable,
  ) : KeysetRepairError

  data class CloudBackupFailed(
    override val message: String = "Failed to update cloud backup",
    override val cause: Throwable,
  ) : KeysetRepairError

  /**
   * Failed to upload descriptor backup to the server.
   */
  data class DescriptorBackupFailed(
    override val message: String = "Failed to upload descriptor backup",
    override val cause: Throwable,
  ) : KeysetRepairError

  /**
   * Failed to activate the keyset on the server.
   */
  data class KeysetActivationFailed(
    override val message: String = "Failed to activate keyset on server",
    override val cause: Throwable,
  ) : KeysetRepairError

  /**
   * The active keyset is missing its private app spending key.
   * This can happen when restoring from a stale backup that has a different active keyset.
   * The user needs to tap their hardware device to generate a new spending key for this keyset.
   */
  data class MissingPrivateKeyForActiveKeyset(
    override val message: String = "Missing private key for active keyset",
    override val cause: Throwable,
    /** The updated keybox that was being built when the error occurred. */
    val updatedKeybox: Keybox,
  ) : KeysetRepairError
}
