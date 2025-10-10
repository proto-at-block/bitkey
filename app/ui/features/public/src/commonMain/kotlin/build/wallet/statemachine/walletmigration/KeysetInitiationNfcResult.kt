package build.wallet.statemachine.walletmigration

import build.wallet.bitkey.hardware.HwKeyBundle
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
)
