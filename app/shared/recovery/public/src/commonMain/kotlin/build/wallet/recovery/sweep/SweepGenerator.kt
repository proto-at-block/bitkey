package build.wallet.recovery.sweep

import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Result

/**
 * Generates PSBTs for sweeping funds to a new destination wallet.
 */
interface SweepGenerator {
  suspend fun generateSweep(keybox: Keybox): Result<List<SweepPsbt>, SweepGeneratorError>

  sealed class SweepGeneratorError : Error() {
    data class AppPrivateKeyMissing(override val cause: Throwable) : SweepGeneratorError()

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
  }
}
