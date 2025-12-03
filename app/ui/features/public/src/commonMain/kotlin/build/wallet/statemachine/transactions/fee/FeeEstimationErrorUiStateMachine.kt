package build.wallet.statemachine.transactions.fee

import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator.FeeEstimationError
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.StateMachine

/**
 * Shared UI state machine for rendering fee-estimation related errors.
 */
interface FeeEstimationErrorUiStateMachine :
  StateMachine<FeeEstimationErrorUiProps, BodyModel>

data class FeeEstimationErrorUiProps(
  val error: FeeEstimationErrorUiError,
  val onBack: () -> Unit,
  val onRetry: (() -> Unit)? = null,
  val errorData: ErrorData,
  val context: FeeEstimationErrorContext = FeeEstimationErrorContext.Send,
)

sealed interface FeeEstimationErrorUiError {
  data object InsufficientFunds : FeeEstimationErrorUiError

  data object SpendBelowDust : FeeEstimationErrorUiError

  data class LoadFailed(val isConnectivityError: Boolean) : FeeEstimationErrorUiError

  data object FeeRateTooLow : FeeEstimationErrorUiError

  data object Generic : FeeEstimationErrorUiError
}

fun FeeEstimationError.toUiError(): FeeEstimationErrorUiError =
  when (this) {
    FeeEstimationError.InsufficientFundsError -> FeeEstimationErrorUiError.InsufficientFunds
    FeeEstimationError.SpendingBelowDustLimitError -> FeeEstimationErrorUiError.SpendBelowDust
    is FeeEstimationError.CannotGetFeesError ->
      FeeEstimationErrorUiError.LoadFailed(isConnectivityError = isConnectivityError)
    FeeEstimationError.NoActiveAccountError,
    FeeEstimationError.NoSpendingWalletFoundError,
    is FeeEstimationError.CannotCreatePsbtError,
    -> FeeEstimationErrorUiError.Generic
  }

fun FeeEstimationError.toErrorData(
  segment: AppSegment,
  actionDescription: String,
): ErrorData =
  ErrorData(
    segment = segment,
    actionDescription = actionDescription,
    cause = this
  )
