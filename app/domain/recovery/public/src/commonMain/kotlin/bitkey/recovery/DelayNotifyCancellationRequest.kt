package bitkey.recovery

import build.wallet.f8e.auth.HwFactorProofOfPossession

/**
 * Represents a request for cancelling a recovery.
 */
sealed interface DelayNotifyCancellationRequest {
  /**
   * Request to cancel "Lost Hardware" recovery. Will be cancelled using
   * app proof of possession.
   */
  data object CancelLostHardwareRecovery : DelayNotifyCancellationRequest

  /**
   * Request to cancel "Lost App and Cloud" recovery. Will be cancelled using
   * hardware proof of possession.
   */
  data class CancelLostAppAndCloudRecovery(
    val hwProofOfPossession: HwFactorProofOfPossession,
  ) : DelayNotifyCancellationRequest
}
