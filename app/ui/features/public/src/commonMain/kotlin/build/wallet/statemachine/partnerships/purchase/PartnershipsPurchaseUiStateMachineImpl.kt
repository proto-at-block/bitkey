@file:OptIn(ExperimentalContracts::class)

package build.wallet.statemachine.partnerships.purchase

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.compose.collections.immutableListOf
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.partnerships.*
import build.wallet.partnerships.PartnershipPurchaseService.NoPurchaseOptionsError
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize.MIN40
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseState.*
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@BitkeyInject(ActivityScope::class)
class PartnershipsPurchaseUiStateMachineImpl(
  val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val partnershipTransactionsService: PartnershipTransactionsService,
  private val partnershipPurchaseService: PartnershipPurchaseService,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val eventTracker: EventTracker,
  private val exchangeRateService: ExchangeRateService,
  private val currencyConverter: CurrencyConverter,
) : PartnershipsPurchaseUiStateMachine {
  @Composable
  override fun model(props: PartnershipsPurchaseUiProps): SheetModel {
    val initialState = PurchaseAmountsState.Loading(preSelectedAmount = null)
    var state: PartnershipsPurchaseState by remember {
      mutableStateOf(PurchaseAmountsState.Loading(preSelectedAmount = props.selectedAmount))
    }
    val exchangeRates: ImmutableList<ExchangeRate> by remember {
      mutableStateOf(exchangeRateService.exchangeRates.value.toImmutableList())
    }
    return when (val currentState = state) {
      is PurchaseAmountsState.Loaded -> {
        return selectPurchaseAmountModel(
          purchaseAmounts = currentState.purchaseAmounts.displayOptions.toImmutableList(),
          selectedAmount = currentState.selectedAmount,
          moneyDisplayFormatter = moneyDisplayFormatter,
          onSelectAmount = { amount ->
            // deselect amount if it's already selected
            val selectedAmount = amount.takeIf { currentState.selectedAmount != amount }
            state = PurchaseAmountsState.Loaded(
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
          onNext = {
            state = QuotesState.Loading(it)
          },
          onExit = props.onExit
        )
      }
      is PurchaseAmountsState.LoadingFailure ->
        if (currentState.error is NoPurchaseOptionsError) {
          failureModel(
            id = DepositEventTrackerScreenId.PARTNER_PURCHASE_OPTIONS_NOT_AVAILABLE,
            error = currentState.error,
            title = "New Partners Coming Soon",
            errorMessage = "Bitkey is actively seeking partnerships with local exchanges to facilitate bitcoin purchases. Until then, you can add bitcoin using the receive button.",
            onBack = props.onBack,
            onExit = props.onExit
          )
        } else {
          failureModel(
            id = DepositEventTrackerScreenId.PARTNER_PURCHASE_OPTIONS_ERROR,
            error = currentState.error,
            errorMessage = "Failed to load purchase amounts.",
            onBack = props.onBack,
            onExit = props.onExit
          )
        }
      is PurchaseAmountsState.Loading -> {
        LaunchedEffect("load-partnerships-purchase-amount") {
          partnershipPurchaseService.getSuggestedPurchaseAmounts()
            .onFailure { error ->
              state = PurchaseAmountsState.LoadingFailure(error)
            }
            .onSuccess {
              val preSelectedAmountIsValid = isValidPurchaseAmount(
                suggestedAmounts = it,
                purchaseAmount = currentState.preSelectedAmount
              )
              state = if (preSelectedAmountIsValid) {
                // Preselected amount is valid - directly loading purchase quote
                QuotesState.Loading(currentState.preSelectedAmount)
              } else {
                // Preselected amount is not valid - asking customer to select an amount
                PurchaseAmountsState.Loaded(purchaseAmounts = it, selectedAmount = it.default)
              }
            }
        }
        loadingModel(
          id = DepositEventTrackerScreenId.LOADING_PARTNER_PURCHASE_OPTIONS,
          onExit = props.onExit
        )
      }

      is QuotesState.Loaded -> {
        LaunchedEffect("track-view-quotes", currentState.quotes) {
          currentState.quotes.forEach { quote ->
            eventTracker.track(
              action = Action.ACTION_APP_PARTNERSHIPS_VIEWED_PURCHASE_QUOTE,
              context = PartnerEventTrackerScreenIdContext(quote.partnerInfo)
            )
          }
        }
        return selectPartnerPurchaseQuoteModel(
          title = "Purchase ${moneyDisplayFormatter.format(currentState.amount)}",
          subTitle = "Offers show the amount you'll receive after exchange fees. Bitkey does not charge a fee.",
          quotes = currentState.quotes.map {
            it.toQuoteModel(moneyDisplayFormatter, exchangeRates, currencyConverter)
          }.toImmutableList(),
          onSelectPartnerQuote = {
            state = RedirectState.Loading(
              amount = currentState.amount,
              quote = it
            )
          },
          onClosed = props.onExit,
          previousPartnerIds = currentState.previousPartnerIds
        )
      }
      is QuotesState.LoadingFailure ->
        failureModel(
          id = DepositEventTrackerScreenId.PARTNER_QUOTES_LIST_ERROR,
          error = currentState.error,
          errorMessage = "Failed to load partner quotes.",
          onBack = { state = initialState },
          onExit = props.onExit
        )
      is QuotesState.Loading -> {
        LaunchedEffect("load-partnerships-quotes") {
          partnershipPurchaseService.loadPurchaseQuotes(currentState.amount)
            .onFailure { error ->
              state = QuotesState.LoadingFailure(error = error)
            }
            .onSuccess { quotes ->
              state = QuotesState.Loaded(
                amount = currentState.amount,
                quotes = quotes.toImmutableList(),
                previousPartnerIds = partnershipTransactionsService.previouslyUsedPartnerIds.first()
              )
            }
        }
        loadingModel(
          id = DepositEventTrackerScreenId.LOADING_PARTNER_QUOTES_LIST,
          onExit = props.onExit
        )
      }
      is RedirectState.Loaded -> {
        val redirectInfo = currentState.redirectInfo
        LaunchedEffect("handle-purchase-redirect", redirectInfo) {
          props.onPartnerRedirected(redirectInfo.redirectMethod, redirectInfo.transaction)
        }
        loadingModel(
          id = DepositEventTrackerScreenId.PURCHASE_PARTNER_REDIRECTING,
          context = PartnerEventTrackerScreenIdContext(redirectInfo.transaction.partnerInfo),
          onExit = props.onExit
        )
      }
      is RedirectState.Loading -> {
        LaunchedEffect("load-purchase-partner-redirect-info") {
          partnershipPurchaseService
            .preparePurchase(currentState.quote, currentState.amount)
            .onFailure { error ->
              state = RedirectState.LoadingFailure(
                partner = currentState.quote.partnerInfo,
                error = error
              )
            }
            .onSuccess {
              state = RedirectState.Loaded(redirectInfo = it)
            }
        }
        loadingModel(
          id = DepositEventTrackerScreenId.LOADING_PURCHASE_PARTNER_REDIRECT,
          context = PartnerEventTrackerScreenIdContext(currentState.quote.partnerInfo),
          onExit = props.onExit
        )
      }
      is RedirectState.LoadingFailure -> {
        val partnerName = currentState.partner.name
        failureModel(
          id = DepositEventTrackerScreenId.PURCHASE_PARTNER_REDIRECT_ERROR,
          context = PartnerEventTrackerScreenIdContext(currentState.partner),
          error = currentState.error,
          errorMessage = "Failed to redirect to $partnerName.",
          onBack = { state = initialState },
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

    return purchaseAmount.currency == fiatCurrency && purchaseAmount.value in suggestedAmounts.min.value..suggestedAmounts.max.value
  }
}

@Composable
private fun failureModel(
  id: DepositEventTrackerScreenId,
  context: PartnerEventTrackerScreenIdContext? = null,
  title: String = "Error",
  error: Throwable?,
  errorMessage: String,
  onBack: () -> Unit,
  onExit: () -> Unit,
): SheetModel {
  LaunchedEffect("partnership-log-error", error, errorMessage) {
    logError(throwable = error) { errorMessage }
  }
  return SheetModel(
    body =
      ErrorFormBodyModel(
        eventTrackerScreenId = id,
        eventTrackerContext = context,
        title = title,
        subline = errorMessage,
        primaryButton = ButtonDataModel("Got it", isLoading = false, onClick = onBack),
        renderContext = Sheet,
        onBack = onBack
      ),
    onClosed = onExit,
    size = MIN40
  )
}

@Composable
private fun loadingModel(
  id: DepositEventTrackerScreenId,
  context: PartnerEventTrackerScreenIdContext? = null,
  onExit: () -> Unit,
) = SheetModel(
  body = LoadingBodyModel(
    id = id,
    context = context,
    onExit = onExit
  ),
  onClosed = onExit,
  size = MIN40
)

data class LoadingBodyModel(
  override val id: DepositEventTrackerScreenId,
  val context: PartnerEventTrackerScreenIdContext? = null,
  val onExit: () -> Unit,
) : FormBodyModel(
    id = id,
    eventTrackerContext = context,
    onBack = {},
    toolbar = null,
    header = null,
    mainContentList = immutableListOf(Loader),
    primaryButton = null,
    renderContext = Sheet
  )

/**
 * Describes state of the data used for the Partnerships purchase flow
 */
private sealed interface PartnershipsPurchaseState {
  sealed interface PurchaseAmountsState : PartnershipsPurchaseState {
    /**
     * Loading default purchase amounts used for partnerships
     *
     * @param preSelectedAmount - amount already selected by the user. If null, suggested amounts will
     * be shown.
     */
    data class Loading(val preSelectedAmount: FiatMoney?) : PurchaseAmountsState

    /**
     * Default purchase amounts have been loaded
     */
    data class Loaded(
      val purchaseAmounts: SuggestedPurchaseAmounts,
      val selectedAmount: FiatMoney?,
    ) : PurchaseAmountsState

    /**
     * Failure in loading the purchase amounts
     */
    data class LoadingFailure(
      val error: Error,
    ) : PurchaseAmountsState
  }

  /**
   * Describes state of the data used for the Partnerships quotes flow
   */
  sealed interface QuotesState : PartnershipsPurchaseState {
    /**
     * Loading partner quotes
     */
    data class Loading(val amount: FiatMoney) : QuotesState

    /**
     * Partner quotes loaded
     */
    data class Loaded(
      val amount: FiatMoney,
      val quotes: ImmutableList<PurchaseQuote>,
      val previousPartnerIds: List<PartnerId>,
    ) : QuotesState

    /**
     * Failure in loading quotes
     */
    data class LoadingFailure(
      val error: Error,
    ) : QuotesState
  }

  /**
   * Describes state of the data used for the partner redirects
   */
  sealed interface RedirectState : PartnershipsPurchaseState {
    /**
     * Loading partner redirect info
     * @param amount - amount to be purchased
     * @param quote - quote to use for the purchase
     * @param paymentMethod - payment method to use
     */
    data class Loading(
      val amount: FiatMoney,
      val quote: PurchaseQuote,
    ) : RedirectState

    /**
     * Partner redirect info loaded
     * @param partner - partner to purchase from
     * @param redirectInfo - redirect info for the partner
     */
    data class Loaded(
      val redirectInfo: PurchaseRedirectInfo,
    ) : RedirectState

    /**
     * Failure in loading partner redirect info
     * @param partner - partner to purchase from
     * @param error - error that occurred
     */
    data class LoadingFailure(
      val partner: PartnerInfo,
      val error: Throwable,
    ) : RedirectState
  }
}
