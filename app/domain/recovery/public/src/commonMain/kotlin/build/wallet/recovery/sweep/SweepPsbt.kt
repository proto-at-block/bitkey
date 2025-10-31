package build.wallet.recovery.sweep

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.spending.SpendingKeyset

/**
 * Represents a [Psbt] that will sweep funds from the source keyset to
 * a destination preset in the [psbt].
 */
data class SweepPsbt(
  /**
   * A psbt will sweep funds from the source keyset to the destination.
   */
  val psbt: Psbt,
  /**
   * The signature plan describing which parties must sign this PSBT.
   */
  val signaturePlan: SweepSignaturePlan,
  /**
   * Keyset from which funds will be swept.
   */
  val sourceKeyset: SpendingKeyset,
  /**
   * The Bitcoin address where funds will be swept to.
   */
  val destinationAddress: String,
)

/**
 * Describes the signing requirements for a sweep PSBT.
 */
sealed class SweepSignaturePlan {
  /**
   * Standard recovery sweep where Hardware was lost - requires App + Server signatures.
   */
  object AppAndServer : SweepSignaturePlan()

  /**
   * Private wallet migration sweep - requires both App + Hardware, no Server.
   * This is used when migrating from legacy multisig to private wallet, or when
   * sweeping from an old multisig keyset after migration to preserve privacy.
   */
  object AppAndHardware : SweepSignaturePlan()

  /**
   * Standard recovery sweep where App was lost - requires Hardware + Server signatures.
   */
  object HardwareAndServer : SweepSignaturePlan()

  /**
   * Whether App signature is required.
   */
  val requiresAppSignature: Boolean
    get() = when (this) {
      AppAndServer, AppAndHardware -> true
      HardwareAndServer -> false
    }

  /**
   * Whether Hardware signature is required.
   */
  val requiresHardwareSignature: Boolean
    get() = when (this) {
      AppAndHardware, HardwareAndServer -> true
      AppAndServer -> false
    }

  /**
   * Whether the server (F8e) signature is required.
   */
  val requiresServerSignature: Boolean
    get() = when (this) {
      AppAndServer, HardwareAndServer -> true
      AppAndHardware -> false
    }
}
