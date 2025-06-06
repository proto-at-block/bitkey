package bitkey.f8e.fingerprintreset

import bitkey.f8e.privilegedactions.PrivilegedActionsF8eClient
import build.wallet.ktor.result.RedactedResponseBody

interface FingerprintResetF8eClient :
  PrivilegedActionsF8eClient<FingerprintResetRequest, FingerprintResetResponse>

/**
 * Request to initiate a fingerprint reset
 */
data class FingerprintResetRequest(
  val version: Int,
  val action: Int,
  val deviceId: String,
  val challenge: List<Int>,
  val signature: String,
  val hwAuthPublicKey: String,
)

/**
 * Response for fingerprint reset after the delay and notify step is complete
 */
data class FingerprintResetResponse(
  val version: Int,
  val serializedRequest: String,
  val signature: String,
) : RedactedResponseBody
