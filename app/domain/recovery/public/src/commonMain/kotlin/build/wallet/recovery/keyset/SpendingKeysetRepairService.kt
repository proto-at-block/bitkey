package build.wallet.recovery.keyset

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwSpendingKeyProof
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.recovery.ListKeysetsResponse
import build.wallet.f8e.recovery.SignedKeysetVerificationResponse
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Service for detecting and repairing keyset mismatches from stale cloud backup recovery.
 *
 * When a user recovers from a stale cloud backup, their local `activeSpendingKeyset`
 * may not match the server's active keyset, or their local keybox may be missing
 * private-wallet keysets that should be authoritative. This service:
 * 1. Detects repair-needed states via [syncStatus] flow (updated by worker on startup/foreground)
 * 2. Provides on-demand repair preparation via [checkPrivateKeysets]
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
   * Checks if the account repair requires SSEK unsealing.
   *
   * @param account The full account to check
   * @return [PrivateKeysetInfo.NeedsUnsealing] when descriptor backups must be decrypted,
   *         [PrivateKeysetInfo.None] when repair can proceed directly from server keysets,
   *         or an error if the check fails
   */
  suspend fun checkPrivateKeysets(
    account: FullAccount,
  ): Result<PrivateKeysetInfo, KeysetRepairError>

  /**
   * Initiates repair process.
   *
   * @param account The full account to repair
   * @param cachedData Cached response data from [checkPrivateKeysets] to avoid duplicate network calls.
   */
  suspend fun attemptRepair(
    account: FullAccount,
    cachedData: KeysetRepairCachedData,
  ): Result<KeysetRepairState.RepairComplete, KeysetRepairError>

  /**
   * Regenerates the active keyset when the app spending private key is missing using the
   * legacy W1 hardware proof-of-possession path.
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
    hwSpendingKeyProof: HwSpendingKeyProof? = null,
  ): Result<KeysetRepairState.RepairComplete, KeysetRepairError>

  /**
   * Creates and locally persists a regenerated keyset without authorizing server activation.
   *
   * W3 repair uses this first because the rotate-spending-keyset action proof must include the
   * new f8e keyset id, which is only known after creating the server keyset.
   */
  suspend fun prepareRegeneratedActiveKeyset(
    account: FullAccount,
    updatedKeybox: Keybox,
    hwSpendingKey: HwSpendingPublicKey,
    hwSpendingKeyProof: HwSpendingKeyProof? = null,
  ): Result<PreparedRegeneratedKeyset, KeysetRepairError>

  /**
   * Finishes a previously prepared regenerated keyset repair.
   *
   * W1 passes [PrivilegedActionProof.HwKeyProof] for both proofs. W3 passes action proofs:
   * update-descriptor-backups for [descriptorBackupProof] when descriptor backup upload is needed,
   * and rotate-spending-keyset for [keysetActivationProof].
   */
  suspend fun completeRegeneratedActiveKeyset(
    account: FullAccount,
    preparedRegeneratedKeyset: PreparedRegeneratedKeyset,
    descriptorBackupProof: PrivilegedActionProof?,
    keysetActivationProof: PrivilegedActionProof,
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
   * Local keybox is missing keysets that are known to the server.
   */
  data class IncompleteKeysetList(
    val activeKeysetId: String,
    val missingKeysetIds: Set<String>,
  ) : SpendingKeysetSyncStatus

  /**
   * Local keybox for a private wallet is incomplete and needs to be backfilled from server data.
   */
  data class IncompletePrivateWallet(
    val activeKeysetId: String,
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
   * Feature flag disabled or no repair-needed condition detected.
   */
  data object NotNeeded : KeysetRepairState

  /**
   * Repair-needed state detected, repair available but not started.
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
    val signedKeysetVerification: SignedKeysetVerificationResponse? = null,
  ) : KeysetRepairState
}

/**
 * A regenerated keyset that has been created on f8e and saved locally, but has not yet been
 * activated on f8e.
 */
data class PreparedRegeneratedKeyset(
  val keybox: Keybox,
  val newKeyset: SpendingKeyset,
)

/**
 * Information about whether repair requires SSEK unsealing.
 * Also contains cached data from the f8e response to avoid duplicate network calls.
 */
sealed interface PrivateKeysetInfo {
  /**
   * SSEK unsealing is not needed.
   * Contains cached response data to pass to [SpendingKeysetRepairService.attemptRepair].
   */
  data class None(
    val cachedResponseData: KeysetRepairCachedData,
  ) : PrivateKeysetInfo

  /**
   * Descriptor-backed private keysets require SSEK unsealing.

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
