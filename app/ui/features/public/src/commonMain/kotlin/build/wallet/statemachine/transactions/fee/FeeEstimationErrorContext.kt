package build.wallet.statemachine.transactions.fee

data class FeeEstimationErrorCopy(
  val title: String,
  val subline: String,
  val primaryButtonText: String = "Go Back",
)

private const val SEND_ERROR_TITLE = "We couldn’t send this transaction"
private const val SPEED_UP_ERROR_TITLE = "We couldn’t speed up this transaction"

private fun sendCopy(subline: String) =
  FeeEstimationErrorCopy(
    title = SEND_ERROR_TITLE,
    subline = subline
  )

private fun speedUpCopy(subline: String) =
  FeeEstimationErrorCopy(
    title = SPEED_UP_ERROR_TITLE,
    subline = subline
  )

enum class FeeEstimationErrorContext(
  private val insufficientFundsCopy: FeeEstimationErrorCopy,
  private val spendBelowDustCopy: FeeEstimationErrorCopy,
  private val genericCopy: FeeEstimationErrorCopy,
  private val feeRateTooLowCopy: FeeEstimationErrorCopy,
  private val loadFeesTitle: String,
) {
  Send(
    insufficientFundsCopy = sendCopy(
      subline = "The amount you are trying to send is too high. Please decrease the amount and try again."
    ),
    spendBelowDustCopy = sendCopy(
      subline = "The amount you are trying to send is too low. Please try increasing the amount and try again."
    ),
    genericCopy = sendCopy(
      subline = "We are looking into this. Please try again later."
    ),
    feeRateTooLowCopy = sendCopy(
      subline = "The current fee rate is too low. Please try again later."
    ),
    loadFeesTitle = "We couldn’t determine fees for this transaction"
  ),
  SpeedUp(
    insufficientFundsCopy = speedUpCopy(
      subline = "There are not enough funds to speed up the transaction. Please add more funds and try again."
    ),
    spendBelowDustCopy = speedUpCopy(
      subline = "The amount you are trying to send is too low. Please try increasing the amount and try again."
    ),
    genericCopy = speedUpCopy(
      subline = "We are looking into this. Please try again later."
    ),
    feeRateTooLowCopy = speedUpCopy(
      subline = "The current fee rate is too low. Please try again later."
    ),
    loadFeesTitle = "We couldn’t determine fees for this transaction"
  ),
  ;

  fun copyFor(error: FeeEstimationErrorUiError): FeeEstimationErrorCopy =
    when (error) {
      FeeEstimationErrorUiError.InsufficientFunds -> insufficientFundsCopy
      FeeEstimationErrorUiError.SpendBelowDust -> spendBelowDustCopy
      FeeEstimationErrorUiError.Generic -> genericCopy
      FeeEstimationErrorUiError.FeeRateTooLow -> feeRateTooLowCopy
      is FeeEstimationErrorUiError.LoadFailed -> error(
        "LoadFailed copy handled via NetworkError copy path"
      )
    }

  fun loadFeesTitle(): String = loadFeesTitle
}
