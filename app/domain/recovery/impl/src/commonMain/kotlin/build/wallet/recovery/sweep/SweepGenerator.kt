package build.wallet.recovery.sweep

import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Result

/**
 * Generates PSBTs for sweeping funds to a new destination wallet.
 */
interface SweepGenerator {
  suspend fun generateSweep(
    keybox: Keybox,
    context: SweepGenerationContext = SweepGenerationContext.Real,
  ): Result<List<SweepPsbt>, SweepGeneratorError>

  sealed class SweepGeneratorError : Error() {
    data class AppPrivateKeyMissing(override val cause: Throwable) : SweepGeneratorError()

    /** Something went wrong in [build.wallet.chaincode.delegation.PsbtUtils] when tweaking the psbt. */
    data class FailedToTweakPsbt(override val cause: Throwable) : SweepGeneratorError()

    /** Error listing keysets from f8e */
    data object FailedToListKeysets : SweepGeneratorError()

    /** Failed to generate a new destination address */
    data class FailedToGenerateDestinationAddress(
      override val cause: Throwable,
    ) : SweepGeneratorError()

    /** Failed to generate a psbt using BDK */
    data class BdkFailedToCreatePsbt(
      override val cause: Throwable,
      val sourceKeyset: SpendingKeyset,
    ) : SweepGeneratorError()

    data class ErrorCreatingWallet(
      override val cause: Throwable,
    ) : SweepGeneratorError()

    data class ErrorSyncingSpendingWallet(
      override val cause: Throwable,
    ) : SweepGeneratorError()

    /**
     * Private wallet keybox does not have local keysets.
     * This should never happen as private wallets require local keysets for chain code delegation.
     */
    data object PrivateWalletMissingLocalKeysets : SweepGeneratorError()
  }
}

sealed interface SweepGenerationContext {
  /**
   * Real sweep generation that uploads the destination address to F8e for monitoring.
   */
  object Real : SweepGenerationContext

  /**
   * Estimate-only sweep generation (e.g., for fee estimation) that skips address upload.
   * Used when generating mock sweeps to estimate fees without actually committing to the sweep.
   */
  object Estimate : SweepGenerationContext
}
