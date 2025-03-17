package build.wallet.statemachine.send.fee

import androidx.compose.runtime.*
import build.wallet.account.AccountService
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bitcoin.fees.BitcoinTransactionBaseCalculator
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator.FeeEstimationError
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator.FeeEstimationError.NoActiveAccountError
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.THIRTY_MINUTES
import build.wallet.bitcoin.transactions.TransactionPriorityPreference
import build.wallet.bitcoin.transactions.getTransactionData
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.*
import build.wallet.statemachine.send.fee.FeeOptionsUiState.*
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId.*
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.immutableMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.first

@BitkeyInject(ActivityScope::class)
class FeeSelectionUiStateMachineImpl(
  private val bitcoinTransactionFeeEstimator: BitcoinTransactionFeeEstimator,
  private val transactionPriorityPreference: TransactionPriorityPreference,
  private val feeOptionListUiStateMachine: FeeOptionListUiStateMachine,
  private val transactionBaseCalculator: BitcoinTransactionBaseCalculator,
  private val bitcoinWalletService: BitcoinWalletService,
  private val accountService: AccountService,
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
              uiState = if (props.preselectedPriority != null) {
                ProceedingWithPreselectedFeeUiState(
                  transactionBaseAmount = transactionBaseAmount,
                  fees = fees,
                  selectedPriority = props.preselectedPriority
                )
              } else {
                SelectingFeeUiState(transactionBaseAmount, fees, defaultPriority)
              }
            }
          )

        is ProceedingWithPreselectedFeeUiState -> {
          // Directly proceed with the preselected fee
          SideEffect {
            props.onContinue(state.selectedPriority, state.fees)
          }

          LoadingBodyModel(
            message = "Processing...",
            onBack = props.onBack,
            id = null,
            eventTrackerShouldTrack = false
          )
        }

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
    val options = feeOptionListUiStateMachine.model(
      props = FeeOptionListProps(
        transactionBaseAmount = state.transactionBaseAmount,
        fees = state.fees,
        defaultPriority = state.selectedPriority,
        exchangeRates = props.exchangeRates,
        onOptionSelected = {
          onFeeSelected(it)
        }
      )
    )

    return FeeOptionsBodyModel(
      title = "Select a transfer speed",
      feeOptions = options,
      primaryButton =
        ButtonModel(
          text = "Continue",
          size = Footer,
          isEnabled = options.options.any { it.selected }, // enable if an option is selected
          onClick = StandardClick {
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
      val bitcoinBalance = bitcoinWalletService.getTransactionData().balance

      val account = accountService.activeAccount().first()
      if (account !is FullAccount) {
        logError {
          "No active full account found, when fetching fee options. Found account: $account."
        }
        onFeesLoadFailed(NoActiveAccountError)
        return@LaunchedEffect
      }

      bitcoinTransactionFeeEstimator.getFeesForTransaction(
        priorities = props.preselectedPriority?.let { listOf(it) }
          ?: EstimatedTransactionPriority.entries,
        account = account,
        recipientAddress = props.recipientAddress,
        amount = props.sendAmount
      )
        .onFailure { onFeesLoadFailed(it) }
        .onSuccess {
          val fees = it.toImmutableMap()

          val minimumTransactionAmount =
            transactionBaseCalculator.minimumSatsRequiredForTransaction(
              walletBalance = bitcoinBalance,
              sendAmount = props.sendAmount,
              fees = fees
            )

          when (props.sendAmount) {
            is ExactAmount -> {
              // If the base transaction amount required is greater than the balance, show insufficient
              // funds screen.
              if (minimumTransactionAmount > bitcoinBalance.total) {
                onFeesLoadFailed(FeeEstimationError.InsufficientFundsError)
                return@LaunchedEffect
              }
            }
            is SendAll -> {
              // If the base transaction amount required is negative, the customer's available funds
              // will not be enough to cover the fees to create the Send All transaction.
              if (minimumTransactionAmount.isNegative) {
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

            // Use `transactionBaseAmount` if we are sending all, since that is calculated using
            // the send amount minus the fastest possible feerate. Else, just take whatever gets
            // passed in here.
            val transactionAmount = when (props.sendAmount) {
              SendAll -> minimumTransactionAmount
              is ExactAmount -> props.sendAmount.money
            }
            onFeesLoaded(transactionAmount, fees, selectedPriority)
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

  data class ProceedingWithPreselectedFeeUiState(
    val transactionBaseAmount: BitcoinMoney,
    val fees: ImmutableMap<EstimatedTransactionPriority, Fee>,
    val selectedPriority: EstimatedTransactionPriority,
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
