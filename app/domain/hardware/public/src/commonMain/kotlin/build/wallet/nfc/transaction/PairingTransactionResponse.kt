package build.wallet.nfc.transaction

import bitkey.account.HardwareType
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingKeyProof
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SealedSsek

sealed interface PairingTransactionResponse {
  /** Hardware type detected from the device firmware. */
  val hardwareType: HardwareType

  /** Fingerprint enrollment was complete and hardware was successfully paired. */
  data class FingerprintEnrolled(
    val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    val keyBundle: HwKeyBundle,
    val spendingKeyProof: HwSpendingKeyProof? = null,
    val sealedCsek: SealedCsek,
    val sealedSsek: SealedSsek,
    val serial: String,
    override val hardwareType: HardwareType,
  ) : PairingTransactionResponse

  /**
   * Fingerprint enrollment was incomplete.
   * @param hardwareType The hardware type detected from the device firmware.
   */
  data class FingerprintNotEnrolled(
    override val hardwareType: HardwareType,
  ) : PairingTransactionResponse

  /**
   * Fingerprint enrollment was not in progress and needed to be started/restarted.
   * @param hardwareType The hardware type detected from the device firmware.
   */
  data class FingerprintEnrollmentStarted(
    override val hardwareType: HardwareType,
  ) : PairingTransactionResponse
}
