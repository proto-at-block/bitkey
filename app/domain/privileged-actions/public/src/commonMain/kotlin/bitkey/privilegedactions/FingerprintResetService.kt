package bitkey.privilegedactions

import bitkey.f8e.fingerprintreset.FingerprintResetRequest
import bitkey.f8e.fingerprintreset.FingerprintResetResponse
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import build.wallet.bitkey.hardware.HwAuthPublicKey
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
    hwAuthPublicKey: HwAuthPublicKey,
    grantRequest: GrantRequest,
  ): Result<PrivilegedActionInstance, PrivilegedActionError>
}
