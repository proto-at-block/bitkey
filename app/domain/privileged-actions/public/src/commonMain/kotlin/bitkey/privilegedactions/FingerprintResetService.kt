package bitkey.privilegedactions

import bitkey.f8e.fingerprintreset.FingerprintResetRequest
import bitkey.f8e.fingerprintreset.FingerprintResetResponse
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import build.wallet.grants.Grant
import build.wallet.grants.GrantRequest
import com.github.michaelbull.result.Result

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
  ): Result<Grant, PrivilegedActionError>

  /**
   * Cancel a fingerprint reset privileged action
   */
  suspend fun cancelFingerprintReset(cancellationToken: String): Result<Unit, PrivilegedActionError>

  /**
   * Fetches the latest pending fingerprint reset privileged action.
   */
  suspend fun getLatestFingerprintResetAction(): Result<PrivilegedActionInstance?, PrivilegedActionError>
}
