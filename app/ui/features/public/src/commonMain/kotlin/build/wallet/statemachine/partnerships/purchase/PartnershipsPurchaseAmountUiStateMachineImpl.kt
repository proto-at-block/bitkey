@file:OptIn(ExperimentalContracts::class)

package build.wallet.statemachine.partnerships.purchase

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.partnerships.PartnershipPurchaseService
import build.wallet.partnerships.PartnershipPurchaseService.NoPurchaseOptionsError
import build.wallet.partnerships.SuggestedPurchaseAmounts
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize.MIN40
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.form.RenderContext.Sheet
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@BitkeyInject(ActivityScope::class)
class PartnershipsPurchaseAmountUiStateMachineImpl(
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val partnershipPurchaseService: PartnershipPurchaseService,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : PartnershipsPurchaseAmountUiStateMachine {
  @Composable
  override fun model(props: PartnershipsPurchaseAmountUiProps): SheetModel {
    var state: State by remember {
      mutableStateOf(State.Loading(preSelectedAmount = props.selectedAmount))
    }

    return when (val currentState = state) {
      is State.Loaded -> {
        selectPurchaseAmountModel(
          purchaseAmounts = currentState.purchaseAmounts.displayOptions.toImmutableList(),
          selectedAmount = currentState.selectedAmount,
          moneyDisplayFormatter = moneyDisplayFormatter,
          onSelectAmount = { amount ->
            // deselect amount if it's already selected
            val selectedAmount = amount.takeIf { currentState.selectedAmount != amount }
            state = State.Loaded(
              purchaseAmounts = currentState.purchaseAmounts,
              selectedAmount = selectedAmount
            )
          },
          onSelectCustomAmount = {
            props.onSelectCustomAmount(
              currentState.purchaseAmounts.min,
              currentState.purchaseAmounts.max
            )
          },
          onNext = { amount ->
            props.onAmountConfirmed(amount)
          },
          onExit = props.onExit
        )
      }

      is State.LoadingFailure ->
        if (currentState.error is NoPurchaseOptionsError) {
          failureModel(
            id = DepositEventTrackerScreenId.PARTNER_PURCHASE_OPTIONS_NOT_AVAILABLE,
            error = currentState.error,
            title = "New Partners Coming Soon",
            errorMessage = "Bitkey is actively seeking partnerships with local exchanges to facilitate bitcoin purchases. Until then, you can add bitcoin using the receive button.",
            onExit = props.onExit
          )
        } else {
          failureModel(
            id = DepositEventTrackerScreenId.PARTNER_PURCHASE_OPTIONS_ERROR,
            error = currentState.error,
            errorMessage = "Failed to load purchase amounts.",
            onExit = props.onExit
          )
        }

      is State.Loading -> {
        LaunchedEffect("load-partnerships-purchase-amount") {
          partnershipPurchaseService.getSuggestedPurchaseAmounts()
            .onFailure { error ->
              state = State.LoadingFailure(error)
            }
            .onSuccess {
              val preSelectedAmountIsValid = isValidPurchaseAmount(
                suggestedAmounts = it,
                purchaseAmount = currentState.preSelectedAmount
              )
              state = if (preSelectedAmountIsValid) {
                // Preselected amount is valid - directly notify parent
                State.Loaded(purchaseAmounts = it, selectedAmount = currentState.preSelectedAmount)
              } else {
                // Preselected amount is not valid - asking customer to select an amount
                State.Loaded(purchaseAmounts = it, selectedAmount = it.default)
              }
            }
        }
        loadingModel(
          id = DepositEventTrackerScreenId.LOADING_PARTNER_PURCHASE_OPTIONS,
          onExit = props.onExit
        )
      }
    }
  }

  /**
   * Returns true if provided [purchaseAmount] is valid in the context of suggested amounts:
   * - has the same currency
   * - is within min and max suggested values
   */
  private fun isValidPurchaseAmount(
    suggestedAmounts: SuggestedPurchaseAmounts,
    purchaseAmount: FiatMoney?,
  ): Boolean {
    contract {
      returns(true) implies (purchaseAmount != null)
    }
    val fiatCurrency = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value
    if (purchaseAmount == null) return false

    return purchaseAmount.currency == fiatCurrency &&
      purchaseAmount.value in suggestedAmounts.min.value..suggestedAmounts.max.value
  }

  private sealed interface State {
    /**
     * Loading default purchase amounts used for partnerships
     *
     * @param preSelectedAmount - amount already selected by the user. If null, suggested amounts will
     * be shown.
     */
    data class Loading(val preSelectedAmount: FiatMoney?) : State

    /**
     * Default purchase amounts have been loaded
     */
    data class Loaded(
      val purchaseAmounts: SuggestedPurchaseAmounts,
      val selectedAmount: FiatMoney?,
    ) : State

    /**
     * Failure in loading the purchase amounts
     */
    data class LoadingFailure(
      val error: Error,
    ) : State
  }
}

@Composable
private fun failureModel(
  id: DepositEventTrackerScreenId,
  title: String = "Error",
  error: Throwable?,
  errorMessage: String,
  onExit: () -> Unit,
): SheetModel {
  LaunchedEffect("partnership-log-error", error, errorMessage) {
    logError(throwable = error) { errorMessage }
  }
  return SheetModel(
    body = ErrorFormBodyModel(
      eventTrackerScreenId = id,
      eventTrackerContext = null,
      title = title,
      subline = errorMessage,
      primaryButton = ButtonDataModel("Got it", isLoading = false, onClick = onExit),
      renderContext = Sheet,
      onBack = onExit
    ),
    onClosed = onExit,
    size = MIN40
  )
}

@Composable
private fun loadingModel(
  id: DepositEventTrackerScreenId,
  onExit: () -> Unit,
) = SheetModel(
  body = AmountLoadingBodyModel(
    id = id,
    onExit = onExit
  ),
  onClosed = onExit,
  size = MIN40
)

private data class AmountLoadingBodyModel(
  override val id: DepositEventTrackerScreenId,
  val onExit: () -> Unit,
) : FormBodyModel(
    id = id,
    eventTrackerContext = null,
    onBack = {},
    toolbar = null,
    header = null,
    mainContentList = immutableListOf(Loader),
    primaryButton = null,
    renderContext = Sheet
  )
