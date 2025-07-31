package bitkey.privilegedactions

import bitkey.f8e.fingerprintreset.FingerprintResetRequest
import bitkey.f8e.fingerprintreset.FingerprintResetResponse
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import build.wallet.db.DbError
import build.wallet.grants.Grant
import build.wallet.grants.GrantRequest
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Service for handling fingerprint reset operations
 */
interface FingerprintResetService : PrivilegedActionService<FingerprintResetRequest, FingerprintResetResponse> {
  /**
   * Create a fingerprint reset privileged action using a GrantRequest
   */
  suspend fun createFingerprintResetPrivilegedAction(
    grantRequest: GrantRequest,
  ): Result<PrivilegedActionInstance, PrivilegedActionError>

  /**
   * Completes the fingerprint reset process and fetches the server-signed Grant.
   * This should be called after the D+N period has expired.
   */
  suspend fun completeFingerprintResetAndGetGrant(
    actionId: String,
    completionToken: String,
  ): Result<Grant, PrivilegedActionError>

  /**
   * Cancel a fingerprint reset privileged action
   */
  suspend fun cancelFingerprintReset(cancellationToken: String): Result<Unit, PrivilegedActionError>

  /**
   * Fetches the latest pending fingerprint reset privileged action.
   */
  suspend fun getLatestFingerprintResetAction(): Result<PrivilegedActionInstance?, PrivilegedActionError>

  /**
   * Observe the latest pending fingerprint reset privileged action, if any.
   *
   * The flow will emit `null` when there is no pending action, or the corresponding
   * [PrivilegedActionInstance] when one exists. Implementations should update this flow whenever
   * the underlying action state changes (e.g. after creating, completing, or cancelling an
   * action).
   */
  fun fingerprintResetAction(): StateFlow<PrivilegedActionInstance?>

  /**
   * Get the current pending fingerprint reset grant, if any exists.
   */
  suspend fun getPendingFingerprintResetGrant(): Result<Grant?, DbError>

  /**
   * Delete the current fingerprint reset grant after successful use.
   */
  suspend fun deleteFingerprintResetGrant(): Result<Unit, DbError>

  /**
   * Observe the current pending fingerprint reset grant, if any exists.
   *
   * The flow will emit `null` when there is no pending grant, or the corresponding
   * [Grant] when one exists in the database.
   */
  fun pendingFingerprintResetGrant(): Flow<Grant?>

  /**
   * Determine the current state of fingerprint reset, combining both server-side actions
   * and persisted grants to provide a single view of what reset options are available.
   */
  suspend fun getFingerprintResetState(): Result<FingerprintResetState, PrivilegedActionError>
}
