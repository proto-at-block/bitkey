package build.wallet.bitkey.recovery

import bitkey.account.HardwareType
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingKeyProof
import build.wallet.f8e.auth.PrivilegedActionProof

data class HardwareKeysForRecovery(
  /**
   * The new key bundle that the HW will use once recovery is complete.
   * Will be the HW portion of the App and Server SpendingKeyset we are sweeping to.
   */
  val newKeyBundle: HwKeyBundle,
  val newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  val proof: PrivilegedActionProof,
  val hardwareType: HardwareType,
  /**
   * Hardware-attested proof for [newKeyBundle].spendingKey, if the tap that derived
   * the spending key returned an attestation signature + cert chain. Server tolerates
   * `null` until the enrollment gate flips on.
   */
  val spendingKeyProof: HwSpendingKeyProof? = null,
)
