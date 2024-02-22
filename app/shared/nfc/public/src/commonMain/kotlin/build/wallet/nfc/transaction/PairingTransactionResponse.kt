package build.wallet.nfc.transaction

import build.wallet.bitkey.hardware.HwKeyBundle
import okio.ByteString

sealed interface PairingTransactionResponse {
  /** Fingerprint enrollment was complete and hardware was successfully paired. */
  data class FingerprintEnrolled(
    val keyBundle: HwKeyBundle,
    val sealedCsek: ByteString,
    val serial: String,
  ) : PairingTransactionResponse

  /** Fingerprint enrollment was incomplete. */
  data object FingerprintNotEnrolled : PairingTransactionResponse

  /** Fingerprint enrollment was not in progress and needed to be restarted. */
  data object FingerprintEnrollmentRestarted : PairingTransactionResponse
}
