package build.wallet.statemachine.send

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.PreBuiltPsbtFlowFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.moneyhome.MoneyHomeAppSegment
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorContext
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiError
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiProps
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiStateMachine
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class SendAmountEntryUiStateMachineImpl(
  private val transferAmountEntryUiStateMachine: TransferAmountEntryUiStateMachine,
  private val preBuiltPsbtFlowFeatureFlag: PreBuiltPsbtFlowFeatureFlag,
  private val bitcoinWalletService: BitcoinWalletService,
  private val feeEstimationErrorUiStateMachine: FeeEstimationErrorUiStateMachine,
) : SendAmountEntryUiStateMachine {
  @Composable
  override fun model(props: SendAmountEntryUiProps): ScreenModel {
    val isPreBuiltPsbtFlowEnabled = preBuiltPsbtFlowFeatureFlag.isEnabled()

    var uiState: UiState by remember {
      mutableStateOf(UiState.ViewingCalculator)
    }

    return when (val state = uiState) {
      is UiState.ViewingCalculator -> {
        transferAmountEntryUiStateMachine.model(
          props = TransferAmountEntryUiProps(
            onBack = props.onBack,
            initialAmount = props.initialAmount,
            exchangeRates = props.exchangeRates,
            allowSendAll = props.allowSendAll,
            onContinueClick = { continueParams ->
              if (isPreBuiltPsbtFlowEnabled) {
                // Transition to building state when feature flag is enabled
                uiState = UiState.BuildingTransactions(
                  sendAmount = continueParams.sendAmount
                )
              } else {
                // Continue with standard flow
                props.onContinueClick(continueParams.sendAmount)
              }
            }
          )
        )
      }

      is UiState.BuildingTransactions -> {
        LaunchedEffect("build-transactions") {
          bitcoinWalletService.createPsbtsForSendAmount(
            sendAmount = state.sendAmount,
            recipientAddress = props.recipientAddress
          ).onSuccess { psbts ->
            // Pass pre-built PSBTs to the next state
            props.onContinueWithPreBuiltPsbts(state.sendAmount, psbts)
          }.onFailure { error ->
            // Transition to error state
            uiState = UiState.ViewingError(
              sendAmount = state.sendAmount,
              error = error
            )
          }
        }

        LoadingBodyModel(
          onBack = {
            uiState = UiState.ViewingCalculator
          },
          id = SendEventTrackerScreenId.SEND_CREATING_PSBT_LOADING,
          eventTrackerShouldTrack = false
        ).asModalFullScreen()
      }

      is UiState.ViewingError -> feeEstimationErrorUiStateMachine.model(
        props = FeeEstimationErrorUiProps(
          error = FeeEstimationErrorUiError.InsufficientFunds,
          onBack = {
            // Return to calculator on back from error
            uiState = UiState.ViewingCalculator
          },
          errorData = ErrorData(
            segment = MoneyHomeAppSegment.Transactions,
            actionDescription = "Building pre-built PSBT for send transaction",
            cause = state.error
          ),
          context = FeeEstimationErrorContext.Send
        )
      ).asModalScreen()
    }
  }
}

private sealed interface UiState {
  /**
   * Customer is viewing the amount calculator.
   */
  data object ViewingCalculator : UiState

  /**
   * Building transactions with pre-built PSBTs.
   */
  data class BuildingTransactions(
    val sendAmount: BitcoinTransactionSendAmount,
  ) : UiState

  /**
   * Viewing error after failed transaction building.
   */
  data class ViewingError(
    val sendAmount: BitcoinTransactionSendAmount,
    val error: Throwable,
  ) : UiState
}
