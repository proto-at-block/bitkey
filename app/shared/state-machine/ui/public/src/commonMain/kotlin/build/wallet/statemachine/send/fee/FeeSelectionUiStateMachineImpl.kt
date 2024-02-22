package build.wallet.statemachine.send.fee

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bitcoin.fees.BitcoinTransactionBaseCalculator
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator.FeeEstimationError
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.THIRTY_MINUTES
import build.wallet.bitcoin.transactions.TransactionPriorityPreference
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.send.fee.FeeOptionsUiState.GenericFeeEstimationFailedErrorUiState
import build.wallet.statemachine.send.fee.FeeOptionsUiState.InsufficientFundsErrorUiState
import build.wallet.statemachine.send.fee.FeeOptionsUiState.LoadingFeeEstimationFailedErrorUiState
import build.wallet.statemachine.send.fee.FeeOptionsUiState.LoadingTransactionInfoUiState
import build.wallet.statemachine.send.fee.FeeOptionsUiState.SelectingFeeUiState
import build.wallet.statemachine.send.fee.FeeOptionsUiState.SpendBelowDustLimitErrorUiState
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId.FEE_ESTIMATION_BELOW_DUST_LIMIT_ERROR_SCREEN
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId.FEE_ESTIMATION_INSUFFICIENT_FUNDS_ERROR_SCREEN
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId.FEE_ESTIMATION_LOAD_FEES_ERROR_SCREEN
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId.FEE_ESTIMATION_PSBT_CONSTRUCTION_ERROR_SCREEN
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.immutableMapOf
import kotlinx.collections.immutable.toImmutableMap

class FeeSelectionUiStateMachineImpl(
  private val bitcoinTransactionFeeEstimator: BitcoinTransactionFeeEstimator,
  private val transactionPriorityPreference: TransactionPriorityPreference,
  private val feeOptionListUiStateMachine: FeeOptionListUiStateMachine,
  private val transactionBaseCalculator: BitcoinTransactionBaseCalculator,
) : FeeSelectionUiStateMachine {
  @Composable
  override fun model(props: FeeSelectionUiProps): BodyModel {
    var uiState: FeeOptionsUiState by remember { mutableStateOf(LoadingTransactionInfoUiState) }

    return uiState.let { state ->
      when (state) {
        LoadingTransactionInfoUiState ->
          LoadingTransactionInfoModel(
            props,
            onFeesLoadFailed = {
              when (it) {
                is FeeEstimationError.InsufficientFundsError -> {
                  uiState = InsufficientFundsErrorUiState
                }
                is FeeEstimationError.SpendingBelowDustLimitError -> {
                  uiState = SpendBelowDustLimitErrorUiState
                }
                is FeeEstimationError.CannotGetFeesError -> {
                  uiState =
                    LoadingFeeEstimationFailedErrorUiState(
                      isConnectivityError = it.isConnectivityError
                    )
                }
                else -> {
                  uiState = GenericFeeEstimationFailedErrorUiState
                }
              }
            },
            onFeesLoaded = { transactionBaseAmount, fees, defaultPriority ->
              uiState = SelectingFeeUiState(transactionBaseAmount, fees, defaultPriority)
            }
          )

        is SelectingFeeUiState ->
          SelectingFeeModel(
            props = props,
            state = state,
            onFeeSelected = {
              uiState = state.copy(selectedPriority = it)
            }
          )

        is InsufficientFundsErrorUiState ->
          ErrorFormBodyModel(
            title = "We couldn’t send this transaction",
            subline = "The amount you are trying to send is too high. Please decrease the amount and try again.",
            primaryButton = ButtonDataModel(text = "Go Back", onClick = props.onBack),
            eventTrackerScreenId = FEE_ESTIMATION_INSUFFICIENT_FUNDS_ERROR_SCREEN
          )

        is SpendBelowDustLimitErrorUiState ->
          ErrorFormBodyModel(
            title = "We couldn’t send this transaction",
            subline = "The amount you are trying to send is too low. Please try increasing the amount and try again.",
            primaryButton = ButtonDataModel(text = "Go Back", onClick = props.onBack),
            eventTrackerScreenId = FEE_ESTIMATION_BELOW_DUST_LIMIT_ERROR_SCREEN
          )

        is LoadingFeeEstimationFailedErrorUiState ->
          NetworkErrorFormBodyModel(
            title = "We couldn’t determine fees for this transaction",
            isConnectivityError = state.isConnectivityError,
            onBack = props.onBack,
            eventTrackerScreenId = FEE_ESTIMATION_LOAD_FEES_ERROR_SCREEN
          )

        is GenericFeeEstimationFailedErrorUiState ->
          ErrorFormBodyModel(
            title = "We couldn’t send this transaction",
            subline = "We are looking into this. Please try again later.",
            primaryButton = ButtonDataModel(text = "Go Back", onClick = props.onBack),
            eventTrackerScreenId = FEE_ESTIMATION_PSBT_CONSTRUCTION_ERROR_SCREEN
          )
      }
    }
  }

  @Composable
  private fun SelectingFeeModel(
    props: FeeSelectionUiProps,
    state: SelectingFeeUiState,
    onFeeSelected: (EstimatedTransactionPriority) -> Unit,
  ): BodyModel {
    val options =
      feeOptionListUiStateMachine.model(
        props =
          FeeOptionListProps(
            accountData = props.accountData,
            fiatCurrency = props.fiatCurrency,
            transactionAmount = state.transactionBaseAmount,
            fees = state.fees,
            defaultPriority = state.defaultPriority,
            exchangeRates = props.exchangeRates,
            onOptionSelected = {
              onFeeSelected(it)
            }
          )
      )

    return FeeOptionsScreenModel(
      title = "Select a transfer speed",
      feeOptions = options,
      primaryButton =
        ButtonModel(
          text = "Continue",
          size = Footer,
          isEnabled = options.options.any { it.selected }, // enable if an option is selected
          onClick =
            Click.standardClick {
              props.onContinue(
                state.selectedPriority,
                state.fees
              )
            }
        ),
      onBack = props.onBack
    )
  }

  @Composable
  private fun LoadingTransactionInfoModel(
    props: FeeSelectionUiProps,
    onFeesLoadFailed: (FeeEstimationError) -> Unit,
    onFeesLoaded: (
      BitcoinMoney,
      ImmutableMap<EstimatedTransactionPriority, Fee>,
      EstimatedTransactionPriority,
    ) -> Unit,
  ): BodyModel {
    LoadingTransactionInfoEffect(props, onFeesLoadFailed, onFeesLoaded)

    return LoadingBodyModel(
      message = "Loading fees...",
      onBack = props.onBack,
      id = SendEventTrackerScreenId.SEND_LOADING_FEE_OPTIONS,
      eventTrackerShouldTrack = false
    )
  }

  @Composable
  private fun LoadingTransactionInfoEffect(
    props: FeeSelectionUiProps,
    onFeesLoadFailed: (FeeEstimationError) -> Unit,
    onFeesLoaded: (
      BitcoinMoney,
      ImmutableMap<EstimatedTransactionPriority, Fee>,
      EstimatedTransactionPriority,
    ) -> Unit,
  ) {
    LaunchedEffect("fetching-fee-options") {
      bitcoinTransactionFeeEstimator.getFeesForTransaction(
        priorities = EstimatedTransactionPriority.entries,
        keyset = props.accountData.account.keybox.activeSpendingKeyset,
        keyboxConfig = props.accountData.account.keybox.config,
        recipientAddress = props.recipientAddress,
        amount = props.sendAmount
      )
        .onFailure { onFeesLoadFailed(it) }
        .onSuccess {
          val fees = it.toImmutableMap()

          val transactionBaseAmount =
            transactionBaseCalculator.minimumSatsRequiredForTransaction(
              walletBalance = props.accountData.transactionsData.balance,
              sendAmount = props.sendAmount,
              fees = fees
            )

          when (props.sendAmount) {
            is ExactAmount -> {
              // If the base transaction amount required is greater than the balance, show insufficient
              // funds screen.
              if (transactionBaseAmount > props.accountData.transactionsData.balance.total) {
                onFeesLoadFailed(FeeEstimationError.InsufficientFundsError)
                return@LaunchedEffect
              }
            }
            is SendAll -> {
              // If the base transaction amount required is negative, the customer's available funds
              // will not be enough to cover the fees to create the Send All transaction.
              if (transactionBaseAmount.isNegative) {
                onFeesLoadFailed(FeeEstimationError.InsufficientFundsError)
                return@LaunchedEffect
              }
            }
          }

          // if we have a priority preference and there is a fee for it, we will mark it as selected
          // otherwise we will default to the 30 min option
          val selectedPriority =
            transactionPriorityPreference.get().let { priority ->
              if (fees.values.distinct().size == 1) {
                FASTEST
              } else if (priority != null && fees.keys.contains(priority)) {
                priority
              } else {
                THIRTY_MINUTES
              }
            }

          if (fees.isEmpty()) {
            // when empty, we will continue with the default priority
            @Suppress("DEPRECATION")
            props.onContinue(selectedPriority, immutableMapOf())
          } else {
            // otherwise we will display the fees
            onFeesLoaded(transactionBaseAmount, fees, selectedPriority)
          }
        }
    }
  }
}

private sealed interface FeeOptionsUiState {
  /**
   * The user is selecting the fee option they want to use.
   * @property transactionBaseAmount the amount, before fees, the user is trying to send.
   * @property defaultPriority the default priority we select when the user goes to the selection screen.
   * @property selectedPriority the priority the user has selected.
   */
  data class SelectingFeeUiState(
    val transactionBaseAmount: BitcoinMoney,
    val fees: ImmutableMap<EstimatedTransactionPriority, Fee>,
    val defaultPriority: EstimatedTransactionPriority,
    val selectedPriority: EstimatedTransactionPriority = defaultPriority,
  ) : FeeOptionsUiState

  /**
   * Calculating fees for the transaction the user wants to assemble.
   */
  data object LoadingTransactionInfoUiState : FeeOptionsUiState

  /**
   * Not enough funds to generate a transfer
   */
  data object InsufficientFundsErrorUiState : FeeOptionsUiState

  /**
   * Trying to spend below dust limit
   */
  data object SpendBelowDustLimitErrorUiState : FeeOptionsUiState

  /**
   * Loading the fee estimates failed
   */
  data class LoadingFeeEstimationFailedErrorUiState(
    val isConnectivityError: Boolean,
  ) : FeeOptionsUiState

  /**
   * Generic fee estimation failures
   */
  data object GenericFeeEstimationFailedErrorUiState : FeeOptionsUiState
}
