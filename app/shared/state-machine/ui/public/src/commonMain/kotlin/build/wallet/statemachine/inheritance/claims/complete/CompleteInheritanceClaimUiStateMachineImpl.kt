package build.wallet.statemachine.inheritance.claims.complete

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.bdk.bindings.BdkError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceService
import build.wallet.inheritance.InheritanceTransactionDetails
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.*
import build.wallet.statemachine.data.money.convertedOrZeroWithRates
import build.wallet.statemachine.inheritance.InheritanceAppSegment
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachineImpl.State.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(AppScope::class)
class CompleteInheritanceClaimUiStateMachineImpl(
  private val inheritanceService: InheritanceService,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val currencyConverter: CurrencyConverter,
  private val exchangeRateService: ExchangeRateService,
  private val moneyFormatter: MoneyDisplayFormatter,
) : CompleteInheritanceClaimUiStateMachine {
  @Composable
  override fun model(props: CompleteInheritanceClaimUiStateMachineProps): ScreenModel {
    var state by remember { mutableStateOf<State>(LoadingTransferDetails) }
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    val exchangeRates by exchangeRateService.exchangeRates.collectAsState()

    when (val currentState = state) {
      LoadingTransferDetails -> {
        LaunchedEffect("Load Inheritance Details") {
          inheritanceService.loadApprovedClaim(props.relationshipId)
            .onSuccess { state = ConfirmTransfer(it) }
            .onFailure {
              state = when (it) {
                is BdkError.InsufficientFunds -> CompletingEmptyWallet(it)
                else -> LoadingDetailsFailed(it)
              }
            }
        }
      }
      is StartingTransfer -> {
        LaunchedEffect("Starting Inheritance Transfer") {
          inheritanceService
            .completeClaimTransfer(
              relationshipId = props.relationshipId,
              details = currentState.details
            )
            .onSuccess { state = Complete(currentState.details) }
            .onFailure { state = TransferFailed(it, currentState.details) }
        }
      }
      is CompletingEmptyWallet -> {
        LaunchedEffect("Completing Empty Wallet") {
          inheritanceService
            .completeClaimWithoutTransfer(props.relationshipId)
            .onSuccess { state = EmptyWallet(currentState.error) }
            .onFailure { state = LoadingDetailsFailed(it) }
        }
      }
      else -> {}
    }

    return when (val currentState = state) {
      is LoadingTransferDetails, is CompletingEmptyWallet -> LoadingBodyModel(
        id = InheritanceEventTrackerScreenId.LoadingClaimDetails,
        onBack = props.onExit
      ).asModalFullScreen()
      is EmptyWallet -> EmptyBenefactorWalletScreenModel(
        onClose = props.onExit
      ).asModalFullScreen()
      is LoadingDetailsFailed -> ErrorFormBodyModel(
        title = "Unable to load inheritance details",
        eventTrackerScreenId = InheritanceEventTrackerScreenId.LoadingClaimDetailsFailure,
        errorData = ErrorData(
          segment = InheritanceAppSegment.BeneficiaryClaim.Complete,
          actionDescription = "Loading Inheritance Claim Details",
          cause = currentState.error
        ),
        primaryButton = ButtonDataModel(
          text = "Retry",
          onClick = {
            state = LoadingTransferDetails
          }
        ),
        secondaryButton = ButtonDataModel(
          text = "Cancel",
          onClick = props.onExit
        ),
        onBack = props.onExit,
        toolbar = ToolbarModel(leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(props.onExit))
      ).asModalFullScreen()
      is ConfirmTransfer -> InheritanceTransferConfirmationScreenModel(
        onBack = props.onExit,
        onTransfer = {
          state = StartingTransfer(currentState.details)
        },
        recipientAddress = currentState.details.recipientAddress.chunkedAddress(),
        amount = moneyFormatter.format(
          convertedOrZeroWithRates(
            converter = currencyConverter,
            fromAmount = currentState.details.psbt.amountBtc + currentState.details.psbt.fee,
            toCurrency = fiatCurrency,
            rates = exchangeRates.toImmutableList()
          ) as FiatMoney
        ),
        fees = moneyFormatter.format(
          convertedOrZeroWithRates(
            converter = currencyConverter,
            fromAmount = currentState.details.psbt.fee,
            toCurrency = fiatCurrency,
            rates = exchangeRates.toImmutableList()
          ) as FiatMoney
        ),
        netReceivePrimary = moneyFormatter.format(
          convertedOrZeroWithRates(
            converter = currencyConverter,
            fromAmount = currentState.details.psbt.amountBtc,
            toCurrency = fiatCurrency,
            rates = exchangeRates.toImmutableList()
          ) as FiatMoney
        ),
        netReceiveSecondary = moneyFormatter.format(
          currentState.details.psbt.amountBtc
        )
      ).asModalFullScreen()
      is StartingTransfer -> LoadingBodyModel(
        id = InheritanceEventTrackerScreenId.StartingTransfer,
        message = "Starting Transfer"
      ).asModalFullScreen()
      is TransferFailed -> ErrorFormBodyModel(
        title = "Transfer Failed",
        eventTrackerScreenId = InheritanceEventTrackerScreenId.TransferFailed,
        errorData = ErrorData(
          segment = InheritanceAppSegment.BeneficiaryClaim.Complete,
          actionDescription = "Starting Inheritance Transfer",
          cause = currentState.error
        ),
        primaryButton = ButtonDataModel(
          text = "Retry",
          onClick = {
            state = StartingTransfer(currentState.transactionDetails)
          }
        ),
        secondaryButton = ButtonDataModel(
          text = "Cancel",
          onClick = props.onExit
        ),
        toolbar = ToolbarModel(leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(props.onExit)),
        onBack = props.onExit
      ).asModalFullScreen()
      is Complete -> InheritanceTransferSuccessScreenModel(
        onBack = props.onExit,
        recipientAddress = currentState.details.recipientAddress.chunkedAddress(),
        amount = moneyFormatter.format(
          convertedOrZeroWithRates(
            converter = currencyConverter,
            fromAmount = currentState.details.psbt.amountBtc + currentState.details.psbt.fee,
            toCurrency = fiatCurrency,
            rates = exchangeRates.toImmutableList()
          ) as FiatMoney
        ),
        fees = moneyFormatter.format(
          convertedOrZeroWithRates(
            converter = currencyConverter,
            fromAmount = currentState.details.psbt.fee,
            toCurrency = fiatCurrency,
            rates = exchangeRates.toImmutableList()
          ) as FiatMoney
        ),
        netReceivePrimary = moneyFormatter.format(
          convertedOrZeroWithRates(
            converter = currencyConverter,
            fromAmount = currentState.details.psbt.amountBtc,
            toCurrency = fiatCurrency,
            rates = exchangeRates.toImmutableList()
          ) as FiatMoney
        ),
        netReceiveSecondary = moneyFormatter.format(
          currentState.details.psbt.amountBtc
        )
      ).asModalFullScreen()
    }
  }

  sealed interface State {
    /**
     * Initial Loading state to fetch the latest claim details.
     */
    data object LoadingTransferDetails : State

    /**
     * Failure to fetch updated claim details.
     */
    data class LoadingDetailsFailed(val error: Throwable) : State

    /**
     * Transfer confirmation screen.
     *
     * At this point the claim has been locked, and the user is viewing
     * the total amount and fees before initiating the transfer.
     */
    data class ConfirmTransfer(
      val details: InheritanceTransactionDetails,
    ) : State

    /**
     * Loading state while the user waits for the transfer to be initiated.
     */
    data class StartingTransfer(
      val details: InheritanceTransactionDetails,
    ) : State

    /**
     * Shown when the transfer failed to initiate.
     */
    data class TransferFailed(
      val error: Throwable,
      val transactionDetails: InheritanceTransactionDetails,
    ) : State

    /**
     * The inheritance wallet had no funds in it. So we are completing the
     * claim without a transfer.
     */
    data class CompletingEmptyWallet(val error: BdkError.InsufficientFunds) : State

    /**
     * Error screen to tell the user that the inheritance wallet was empty :(
     */
    data class EmptyWallet(val error: BdkError.InsufficientFunds) : State

    /**
     * Success Screen when transfer has been successfully initiated
     */
    data class Complete(
      val details: InheritanceTransactionDetails,
    ) : State
  }
}
