package build.wallet.statemachine.send

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.PreBuiltPsbtFlowFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.money.BitcoinMoney
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.moneyhome.MoneyHomeAppSegment
import build.wallet.statemachine.send.utxo.UtxoSelectionUiProps
import build.wallet.statemachine.send.utxo.UtxoSelectionUiStateMachine
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorContext
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiError
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiProps
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiStateMachine
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class SendAmountEntryUiStateMachineImpl(
  private val transferAmountEntryUiStateMachine: TransferAmountEntryUiStateMachine,
  private val utxoSelectionUiStateMachine: UtxoSelectionUiStateMachine,
  private val preBuiltPsbtFlowFeatureFlag: PreBuiltPsbtFlowFeatureFlag,
  private val bitcoinWalletService: BitcoinWalletService,
  private val feeEstimationErrorUiStateMachine: FeeEstimationErrorUiStateMachine,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
) : SendAmountEntryUiStateMachine {
  @Composable
  override fun model(props: SendAmountEntryUiProps): ScreenModel {
    val isPreBuiltPsbtFlowEnabled = preBuiltPsbtFlowFeatureFlag.isEnabled()

    var uiState: UiState by remember {
      mutableStateOf(UiState.ViewingCalculator)
    }

    return when (val state = uiState) {
      is UiState.ViewingCalculator -> {
        val coinControlLabel = props.coinControl?.let { control ->
          val countLabel = if (control.count == 1) "1 coin" else "${control.count} coins"
          "$countLabel · ${moneyDisplayFormatter.format(control.spendableTotal)}"
        }

        transferAmountEntryUiStateMachine.model(
          props = TransferAmountEntryUiProps(
            onBack = props.onBack,
            initialAmount = props.initialAmount,
            exchangeRates = props.exchangeRates,
            flow = TransferAmountEntryUiProps.Flow.Send(
              allowSendAll = props.allowSendAll,
              coinControlLabel = coinControlLabel,
              onChooseCoinsClick = { enteredAmount ->
                uiState = UiState.ChoosingUtxos(targetAmount = enteredAmount)
              },
              onClearCoinControl = {
                props.onCoinControlChanged(null)
              }
            ),
            onContinueClick = { continueParams ->
              if (isPreBuiltPsbtFlowEnabled) {
                uiState = UiState.BuildingTransactions(
                  sendAmount = continueParams.sendAmount
                )
              } else {
                props.onContinueClick(continueParams.sendAmount)
              }
            }
          )
        )
      }

      is UiState.ChoosingUtxos -> utxoSelectionUiStateMachine.model(
        props = UtxoSelectionUiProps(
          targetAmount = state.targetAmount,
          initialSelection = props.coinControl,
          onConfirm = { coinControl ->
            props.onCoinControlChanged(coinControl)
            uiState = UiState.ViewingCalculator
          },
          onClear = {
            props.onCoinControlChanged(null)
            uiState = UiState.ViewingCalculator
          },
          onBack = {
            uiState = UiState.ViewingCalculator
          }
        )
      )

      is UiState.BuildingTransactions -> {
        LaunchedEffect("build-transactions") {
          bitcoinWalletService.createPsbtsForSendAmount(
            sendAmount = state.sendAmount,
            recipientAddress = props.recipientAddress,
            coinControl = props.coinControl
          ).onSuccess { psbts ->
            props.onContinueWithPreBuiltPsbts(state.sendAmount, psbts)
          }.onFailure { error ->
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
  data object ViewingCalculator : UiState

  data class ChoosingUtxos(
    val targetAmount: BitcoinMoney,
  ) : UiState

  data class BuildingTransactions(
    val sendAmount: BitcoinTransactionSendAmount,
  ) : UiState

  data class ViewingError(
    val sendAmount: BitcoinTransactionSendAmount,
    val error: Throwable,
  ) : UiState
}
