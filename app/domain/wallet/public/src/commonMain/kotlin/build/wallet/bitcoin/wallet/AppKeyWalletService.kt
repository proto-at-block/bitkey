package build.wallet.bitcoin.wallet

import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Result
import kotlin.jvm.JvmInline

interface AppKeyWalletService {
  suspend fun generateAddressInfo(keyset: SpendingKeyset): Result<build.wallet.bitcoin.address.BitcoinAddressInfo, AppKeyWalletServiceError>
  suspend fun fullScan(keyset: SpendingKeyset): Result<AppKeyWalletBalance, AppKeyWalletServiceError>
  suspend fun prepareSweep(keyset: SpendingKeyset): Result<AppKeyWalletSweepPreview, AppKeyWalletServiceError>
  suspend fun signSweep(keyset: SpendingKeyset, previewId: AppKeyWalletSweepId): Result<Unit, AppKeyWalletServiceError>
  suspend fun broadcastSweep(keyset: SpendingKeyset, previewId: AppKeyWalletSweepId): Result<AppKeyWalletSweepResult, AppKeyWalletServiceError>
}

data class AppKeyWalletBalance(val totalSats: ULong, val spendableSats: ULong)

@JvmInline
value class AppKeyWalletSweepId(val value: String)

data class AppKeyWalletSweepPreview(
  val id: AppKeyWalletSweepId,
  val sourceBalance: BitcoinMoney,
  val transferAmount: BitcoinMoney,
  val fee: BitcoinMoney,
)

data class AppKeyWalletSweepResult(val transactionId: String)

sealed class AppKeyWalletServiceError(
  override val cause: Throwable? = null,
  override val message: String,
) : Error(message, cause) {
  class PrivateKeyUnavailable : AppKeyWalletServiceError(message = "The app spending key is unavailable")
  class WalletCreationFailed(cause: Throwable) : AppKeyWalletServiceError(cause, "Could not open the app-key wallet")
  class FullScanFailed(cause: Throwable) : AppKeyWalletServiceError(cause, "Could not scan the app-key wallet")
  class NoSpendableFunds : AppKeyWalletServiceError(message = "No spendable funds were found")
  class DestinationFailed(cause: Throwable) : AppKeyWalletServiceError(cause, "Could not create the destination")
  class ActiveWalletUnavailable : AppKeyWalletServiceError(message = "The active wallet is unavailable")
  class DestinationValidationFailed(cause: Throwable) :
    AppKeyWalletServiceError(cause, "Could not validate the destination")
  class InvalidDestination : AppKeyWalletServiceError(message = "The destination is not owned by the active wallet")
  class PrepareFailed(cause: Throwable) : AppKeyWalletServiceError(cause, "Could not prepare the transfer")
  class StalePreview : AppKeyWalletServiceError(message = "The transfer details changed. Review them again")
  class SignFailed(cause: Throwable) : AppKeyWalletServiceError(cause, "Could not sign the transfer")
  class BroadcastFailed(cause: Throwable) : AppKeyWalletServiceError(cause, "Could not broadcast the transfer")
}
