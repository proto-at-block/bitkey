package build.wallet.statemachine.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_ATTEMPT_SPEED_UP_TRANSACTION
import build.wallet.bitcoin.explorer.BitcoinExplorer
import build.wallet.bitcoin.explorer.BitcoinExplorerType.Mempool
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.SpeedUpTransactionDetails
import build.wallet.bitcoin.transactions.toSpeedUpTransactionDetails
import build.wallet.compose.collections.immutableListOf
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.log
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.platform.web.BrowserNavigator
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.data.money.convertedOrZero
import build.wallet.statemachine.send.SendEntryPoint
import build.wallet.statemachine.send.SendUiProps
import build.wallet.statemachine.send.SendUiStateMachine
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId
import build.wallet.statemachine.transactions.TransactionDetailsUiStateMachineImpl.UiState.FeeLoadingErrorUiState
import build.wallet.statemachine.transactions.TransactionDetailsUiStateMachineImpl.UiState.InsufficientFundsUiState
import build.wallet.statemachine.transactions.TransactionDetailsUiStateMachineImpl.UiState.ShowingTransactionDetailUiState
import build.wallet.statemachine.transactions.TransactionDetailsUiStateMachineImpl.UiState.SpeedingUpTransactionUiState
import build.wallet.time.DateTimeFormatter
import build.wallet.time.DurationFormatter
import build.wallet.time.TimeZoneProvider
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

class TransactionDetailsUiStateMachineImpl(
  private val bitcoinExplorer: BitcoinExplorer,
  private val timeZoneProvider: TimeZoneProvider,
  private val dateTimeFormatter: DateTimeFormatter,
  private val currencyConverter: CurrencyConverter,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val sendUiStateMachine: SendUiStateMachine,
  private val bitcoinTransactionFeeEstimator: BitcoinTransactionFeeEstimator,
  private val clock: Clock,
  private val durationFormatter: DurationFormatter,
  private val eventTracker: EventTracker,
) : TransactionDetailsUiStateMachine {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: TransactionDetailsUiProps): ScreenModel {
    val totalAmount =
      when {
        props.transaction.incoming -> props.transaction.subtotal
        else -> props.transaction.total
      }

    val fiatAmount =
      convertedOrZero(
        converter = currencyConverter,
        fromAmount = totalAmount,
        toCurrency = props.fiatCurrency,
        atTime = props.transaction.broadcastTime ?: props.transaction.confirmationTime()
      )
    val fiatString = moneyDisplayFormatter.format(fiatAmount)
    val feeBumpEnabled by remember {
      mutableStateOf(
        !props.transaction.incoming &&
          props.transaction.confirmationStatus == Pending
      )
    }

    var uiState: UiState by remember { mutableStateOf(ShowingTransactionDetailUiState()) }

    return when (val state = uiState) {
      is SpeedingUpTransactionUiState ->
        sendUiStateMachine.model(
          SendUiProps(
            entryPoint =
              SendEntryPoint.SpeedUp(
                speedUpTransactionDetails = state.speedUpTransactionDetails,
                fiatMoney =
                  FiatMoney(
                    currency = props.fiatCurrency,
                    value = fiatAmount.value
                  ),
                spendingLimit = props.accountData.mobilePayData.spendingLimit,
                newFeeRate = state.chosenFeeRate,
                fees = state.fees
              ),
            accountData = props.accountData,
            fiatCurrency = props.fiatCurrency,
            onExit = {
              uiState = ShowingTransactionDetailUiState()
            },
            onDone = {
              props.onClose()
            },
            validInvoiceInClipboard = null
          )
        )

      is ShowingTransactionDetailUiState ->
        TransactionDetailModel(
          props = props,
          sendAmount = props.transaction.subtotal,
          totalAmount = totalAmount,
          fiatString = fiatString,
          feeBumpEnabled = feeBumpEnabled,
          isLoadingRates = state.isLoadingRates,
          onLoaded = { browserNavigator ->
            uiState = state.copy(browserNavigator = browserNavigator)
          },
          onViewTransaction = {
            state.browserNavigator?.open(
              bitcoinExplorer.getTransactionUrl(
                txId = props.transaction.id,
                network = props.accountData.account.config.bitcoinNetworkType,
                explorerType = Mempool
              )
            )
          },
          onSpeedUpTransaction = {
            eventTracker.track(ACTION_APP_ATTEMPT_SPEED_UP_TRANSACTION)
            uiState = state.copy(isLoadingRates = true)
          },
          onFeesLoadFailure = { error ->
            log(Error) { "Error loading fees: ${error.message}" }
            uiState = when (error) {
              is FeeLoadingError.FeeEstimationError -> {
                when (error.feeEstimationError) {
                  is BitcoinTransactionFeeEstimator.FeeEstimationError.InsufficientFundsError -> InsufficientFundsUiState
                  else -> FeeLoadingErrorUiState(error)
                }
              }
              else -> FeeLoadingErrorUiState(error)
            }
          },
          onFeesLoaded = { fastestFeeRate, feeMap ->
            when (val speedUpTransactionDetails = props.transaction.toSpeedUpTransactionDetails()) {
              // Should be unexpected, but we show an error message here instead of failing silently.
              null -> FeeLoadingErrorUiState(FeeLoadingError.TransactionMissingRecipientAddress)
              else -> {
                uiState =
                  SpeedingUpTransactionUiState(
                    speedUpTransactionDetails = speedUpTransactionDetails,
                    chosenFeeRate = fastestFeeRate,
                    fees = feeMap
                  )
              }
            }
          }
        )

      // TODO [W-5841]: refactor to use common error-handling state machine with FeeSelectionUiStateMachine
      is FeeLoadingErrorUiState ->
        ErrorFormBodyModel(
          title = "We couldn’t speed up this transaction",
          subline = "We are looking into this. Please try again later.",
          primaryButton =
            ButtonDataModel(
              text = "Go Back",
              onClick = { uiState = ShowingTransactionDetailUiState() }
            ),
          eventTrackerScreenId = FeeSelectionEventTrackerScreenId.FEE_ESTIMATION_INSUFFICIENT_FUNDS_ERROR_SCREEN
        ).asModalScreen()

      is InsufficientFundsUiState ->
        ErrorFormBodyModel(
          title = "We couldn’t send this transaction",
          subline = "The amount you are trying to send is too high. Please decrease the amount and try again.",
          primaryButton = ButtonDataModel(
            text = "Go Back",
            onClick = { uiState = ShowingTransactionDetailUiState() }
          ),
          eventTrackerScreenId = null
        ).asModalScreen()
    }
  }

  @Composable
  private fun TransactionDetailModel(
    props: TransactionDetailsUiProps,
    sendAmount: BitcoinMoney,
    totalAmount: BitcoinMoney,
    fiatString: String,
    feeBumpEnabled: Boolean,
    isLoadingRates: Boolean,
    onLoaded: (BrowserNavigator) -> Unit,
    onViewTransaction: () -> Unit,
    onSpeedUpTransaction: () -> Unit,
    onFeesLoadFailure: (FeeLoadingError) -> Unit,
    onFeesLoaded: (FeeRate, ImmutableMap<EstimatedTransactionPriority, Fee>) -> Unit,
  ): ScreenModel {
    if (isLoadingRates) {
      LaunchedEffect("fetch-transaction-fees") {
        when (val recipientAddress = props.transaction.recipientAddress) {
          null -> onFeesLoadFailure(FeeLoadingError.TransactionMissingRecipientAddress)
          else ->
            bitcoinTransactionFeeEstimator.getFeesForTransaction(
              priorities = EstimatedTransactionPriority.entries,
              keyset = props.accountData.account.keybox.activeSpendingKeyset,
              fullAccountConfig = props.accountData.account.keybox.config,
              recipientAddress = recipientAddress,
              amount = BitcoinTransactionSendAmount.ExactAmount(sendAmount)
            )
              .onFailure { onFeesLoadFailure(FeeLoadingError.FeeEstimationError(it)) }
              .onSuccess { feeMap ->
                // We always attempt to pick the fastest fee rate for speed-ups.
                when (val fee = feeMap[EstimatedTransactionPriority.FASTEST]) {
                  null -> onFeesLoadFailure(FeeLoadingError.MissingFeeRate)
                  else -> onFeesLoaded(fee.feeRate, feeMap.toImmutableMap())
                }
              }
        }
      }
    }

    return TransactionDetailModel(
      feeBumpEnabled = feeBumpEnabled,
      txStatusModel = when (props.transaction.confirmationStatus) {
        is Pending -> TxStatusModel.Pending(
          isIncoming = props.transaction.incoming,
          recipientAddress = props.transaction.chunkedRecipientAddress(),
          // Some transactions may not have an estimate confirmation time, if they don't, we don't
          // attempt to show "Transaction delayed", just "Transaction pending".
          isLate = props.transaction.estimatedConfirmationTime?.let { estimatedTime ->
            clock.now() > estimatedTime
          } ?: false
        )
        is Confirmed -> TxStatusModel.Confirmed(
          isIncoming = props.transaction.incoming,
          recipientAddress = props.transaction.chunkedRecipientAddress()
        )
      },
      isLoading = isLoadingRates,
      onLoaded = onLoaded,
      onViewTransaction = onViewTransaction,
      onClose = props.onClose,
      onSpeedUpTransaction = onSpeedUpTransaction,
      content =
        immutableListOf(
          DataList(
            items =
              listOfNotNull(
                when (val status = props.transaction.confirmationStatus) {
                  is Confirmed ->
                    Data(
                      title = "Confirmed at",
                      sideText =
                        dateTimeFormatter.shortDateWithTime(
                          localDateTime =
                            status.blockTime.timestamp.toLocalDateTime(
                              timeZoneProvider.current()
                            )
                        )
                    )

                  is Pending -> pendingDataListItem(props.transaction.estimatedConfirmationTime)
                }
              ).toImmutableList()
          ),
          DataList(
            items =
              when {
                props.transaction.incoming ->
                  immutableListOf(
                    Data(
                      title = "Amount received",
                      sideText =
                        moneyDisplayFormatter
                          .format(props.transaction.subtotal)
                    )
                  )

                else ->
                  immutableListOf(
                    Data(
                      title =
                        when (props.transaction.confirmationStatus) {
                          is Pending -> "Recipient receives"
                          is Confirmed -> "Recipient received"
                        },
                      sideText =
                        moneyDisplayFormatter
                          .format(props.transaction.subtotal)
                    ),
                    Data(
                      title = "Network fees",
                      sideText =
                        (props.transaction.fee ?: BitcoinMoney.zero()).let {
                          moneyDisplayFormatter.format(it)
                        }
                    )
                  )
              },
            total =
              Data(
                title = "Total",
                sideText = moneyDisplayFormatter.format(totalAmount),
                sideTextType = Data.SideTextType.BODY2BOLD,
                secondarySideText =
                  when {
                    props.transaction.broadcastTime != null ->
                      "$fiatString at time sent"

                    props.transaction.confirmationTime() != null ->
                      "$fiatString at time confirmed"

                    else ->
                      "~$fiatString"
                  }
              )
          )
        )
    ).asModalScreen()
  }

  private fun pendingDataListItem(estimatedConfirmationTime: Instant?): Data {
    return estimatedConfirmationTime?.let { confirmationTime ->
      val currentTime = clock.now()
      if (confirmationTime < currentTime) {
        Data(
          title = "Should have arrived by",
          sideText =
            dateTimeFormatter.shortDateWithTime(
              localDateTime = confirmationTime.toLocalDateTime(timeZoneProvider.current())
            ),
          sideTextTreatment = Data.SideTextTreatment.STRIKETHROUGH,
          sideTextType = Data.SideTextType.REGULAR,
          secondarySideText = "${durationFormatter.formatWithAlphabet(currentTime - confirmationTime)} late",
          secondarySideTextType = Data.SideTextType.BOLD,
          secondarySideTextTreatment = Data.SideTextTreatment.WARNING,
          explainer =
            Data.Explainer(
              title = "Taking longer than usual",
              subtitle = "You can either wait for this transaction to be confirmed or speed it up – you'll need to pay a higher network fee."
            )
        )
      } else {
        Data(
          title = "Should arrive by",
          sideText =
            dateTimeFormatter.shortDateWithTime(
              localDateTime = confirmationTime.toLocalDateTime(timeZoneProvider.current())
            )
        )
      }
    } ?: Data(
      title = "Confirmed at",
      sideText = "Unconfirmed"
    )
  }

  private sealed interface UiState {
    /**
     * Customer is viewing transaction details.
     */
    data class ShowingTransactionDetailUiState(
      val browserNavigator: BrowserNavigator? = null,
      val isLoadingRates: Boolean = false,
    ) : UiState

    /**
     * Customer is showing speed up confirmation flow.
     */
    data class SpeedingUpTransactionUiState(
      val speedUpTransactionDetails: SpeedUpTransactionDetails,
      val chosenFeeRate: FeeRate,
      val fees: ImmutableMap<EstimatedTransactionPriority, Fee>,
    ) : UiState

    /**
     * User currently does not have enough funds to fee bump the transaction.
     */
    data object InsufficientFundsUiState : UiState

    /**
     * We failed to construct a fee estimation required to fee bump a transaction.
     */
    data class FeeLoadingErrorUiState(
      val feeLoadingError: FeeLoadingError,
    ) : UiState
  }

  /**
   * Describes different ways loading fees can fail when speeding up a transaction.
   */
  sealed class FeeLoadingError : kotlin.Error() {
    /**
     * Represents an error we encounter when assembling an estimate using
     * [BitcoinTransactionFeeEstimator].
     */
    data class FeeEstimationError(
      val feeEstimationError: BitcoinTransactionFeeEstimator.FeeEstimationError,
    ) : FeeLoadingError()

    /**
     * When attempting to speed up our transaction, we were returned a response without the
     * desired fee rate.
     *
     * In the current implementation, that always is the fastest fee rate, by default.
     */
    data object MissingFeeRate : FeeLoadingError()

    /**
     * The transaction that was loaded to props was missing a recipient address.
     *
     * We do not expect this to happen, but handle it just in case.
     */
    data object TransactionMissingRecipientAddress : FeeLoadingError()
  }
}
