package build.wallet.statemachine.walletmigration

import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingKeyProof
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Sek
import build.wallet.f8e.auth.HwFactorProofOfPossession

/**
 * Data gathered from the NFC interaction before creating a new keyset.
 */
internal data class KeysetInitiationNfcResult(
  /**
   * Hardware proof-of-posession to be used when creating the new keyset.
   */
  val proofOfPossession: HwFactorProofOfPossession,
  /**
   * Newly generated hardware key bundle.
   */
  val newHwKeys: HwKeyBundle,
  /**
   * Hardware-attested proof for [newHwKeys.spendingKey], when the firmware
   * produced one during derivation. Null for pre-attestation firmware.
   */
  val spendingKeyProof: HwSpendingKeyProof? = null,
  /**
   * Storage encryption key to be used for encrypting descriptor backups.
   */
  val ssek: Sek,
  /**
   * Hardware encrypted version of the newly created storage key.
   */
  val sealedSsek: SealedSsek,
)
