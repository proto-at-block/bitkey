package build.wallet.statemachine.partnerships.purchase

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.CashAppFeePromotionFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logError
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.partnerships.*
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first

@BitkeyInject(ActivityScope::class)
class PartnershipsPurchaseQuotesUiStateMachineImpl(
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val partnershipTransactionsService: PartnershipTransactionsService,
  private val partnershipPurchaseService: PartnershipPurchaseService,
  private val eventTracker: EventTracker,
  private val exchangeRateService: ExchangeRateService,
  private val currencyConverter: CurrencyConverter,
  private val cashAppFeePromotionFeatureFlag: CashAppFeePromotionFeatureFlag,
) : PartnershipsPurchaseQuotesUiStateMachine {
  @Composable
  override fun model(props: PartnershipsPurchaseQuotesUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.LoadingQuotes) }
    var showCashAppInfoSheet by remember { mutableStateOf(false) }
    val exchangeRates: ImmutableList<ExchangeRate> by remember {
      mutableStateOf(exchangeRateService.exchangeRates.value.toImmutableList())
    }

    return when (val currentState = state) {
      is State.LoadingQuotes -> {
        LaunchedEffect("load-partnerships-quotes") {
          partnershipPurchaseService.loadPurchaseQuotes(props.purchaseAmount)
            .onFailure { error ->
              state = State.QuotesLoadingFailure(error = error)
            }
            .onSuccess { quotes ->
              state = State.QuotesLoaded(
                quotes = quotes.toImmutableList(),
                previousPartnerIds = partnershipTransactionsService.previouslyUsedPartnerIds.first()
              )
            }
        }
        LoadingBodyModel(
          id = DepositEventTrackerScreenId.LOADING_PARTNER_QUOTES_LIST,
          onBack = props.onBack
        ).asModalFullScreen()
      }

      is State.QuotesLoaded -> {
        LaunchedEffect("track-view-quotes", currentState.quotes) {
          currentState.quotes.forEach { quote ->
            eventTracker.track(
              action = Action.ACTION_APP_PARTNERSHIPS_VIEWED_PURCHASE_QUOTE,
              context = PartnerEventTrackerScreenIdContext(quote.partnerInfo)
            )
          }
        }

        val cashAppQuote = currentState.quotes.firstOrNull {
          it.partnerInfo.partnerId == PartnerId("CashApp")
        }

        val sheetModel = if (showCashAppInfoSheet) {
          cashAppInfoSheetModel(
            cashAppQuote = cashAppQuote,
            onDismiss = { showCashAppInfoSheet = false }
          )
        } else {
          null
        }

        selectPartnerPurchaseQuoteModel(
          title = "Purchase ${moneyDisplayFormatter.format(props.purchaseAmount)}",
          subTitle = "Offers show the amount you'll receive after exchange fees. Bitkey does not charge a fee.",
          quotes = currentState.quotes.map {
            it.toQuoteModel(moneyDisplayFormatter, exchangeRates, currencyConverter)
          }.toImmutableList(),
          onSelectPartnerQuote = { quote ->
            state = State.LoadingRedirect(quote = quote)
          },
          onClosed = props.onBack,
          previousPartnerIds = currentState.previousPartnerIds,
          onShowCashAppInfo = { showCashAppInfoSheet = true },
          isCashAppPromotionEnabled = cashAppFeePromotionFeatureFlag.isEnabled()
        ).asModalFullScreen(sheetModel)
      }

      is State.QuotesLoadingFailure -> {
        failureScreenModel(
          id = DepositEventTrackerScreenId.PARTNER_QUOTES_LIST_ERROR,
          error = currentState.error,
          errorMessage = "Failed to load partner quotes.",
          onBack = props.onBack
        )
      }

      is State.LoadingRedirect -> {
        LaunchedEffect("load-purchase-partner-redirect-info") {
          partnershipPurchaseService
            .preparePurchase(currentState.quote, props.purchaseAmount)
            .onFailure { error ->
              state = State.RedirectLoadingFailure(
                partner = currentState.quote.partnerInfo,
                error = error
              )
            }
            .onSuccess {
              state = State.RedirectLoaded(redirectInfo = it)
            }
        }
        LoadingBodyModel(
          id = DepositEventTrackerScreenId.LOADING_PURCHASE_PARTNER_REDIRECT,
          eventTrackerContext = PartnerEventTrackerScreenIdContext(currentState.quote.partnerInfo),
          onBack = props.onBack
        ).asModalFullScreen()
      }

      is State.RedirectLoaded -> {
        val redirectInfo = currentState.redirectInfo
        LaunchedEffect("handle-purchase-redirect", redirectInfo) {
          props.onPartnerRedirected(redirectInfo.redirectMethod, redirectInfo.transaction)
        }
        LoadingBodyModel(
          id = DepositEventTrackerScreenId.PURCHASE_PARTNER_REDIRECTING,
          eventTrackerContext = PartnerEventTrackerScreenIdContext(redirectInfo.transaction.partnerInfo),
          onBack = props.onBack
        ).asModalFullScreen()
      }

      is State.RedirectLoadingFailure -> {
        val partnerName = currentState.partner.name
        failureScreenModel(
          id = DepositEventTrackerScreenId.PURCHASE_PARTNER_REDIRECT_ERROR,
          context = PartnerEventTrackerScreenIdContext(currentState.partner),
          error = currentState.error,
          errorMessage = "Failed to redirect to $partnerName.",
          onBack = { state = State.LoadingQuotes }
        )
      }
    }
  }

  private sealed interface State {
    data object LoadingQuotes : State

    data class QuotesLoaded(
      val quotes: ImmutableList<PurchaseQuote>,
      val previousPartnerIds: List<PartnerId>,
    ) : State

    data class QuotesLoadingFailure(
      val error: Error,
    ) : State

    data class LoadingRedirect(
      val quote: PurchaseQuote,
    ) : State

    data class RedirectLoaded(
      val redirectInfo: PurchaseRedirectInfo,
    ) : State

    data class RedirectLoadingFailure(
      val partner: PartnerInfo,
      val error: Throwable,
    ) : State
  }
}

@Composable
private fun failureScreenModel(
  id: DepositEventTrackerScreenId,
  context: PartnerEventTrackerScreenIdContext? = null,
  error: Throwable?,
  errorMessage: String,
  onBack: () -> Unit,
): ScreenModel {
  LaunchedEffect("partnership-log-error", error, errorMessage) {
    logError(throwable = error) { errorMessage }
  }
  return ScreenModel(
    body = ErrorFormBodyModel(
      eventTrackerScreenId = id,
      eventTrackerContext = context,
      title = "Error",
      subline = errorMessage,
      primaryButton = ButtonDataModel("Got it", isLoading = false, onClick = onBack),
      renderContext = RenderContext.Screen,
      onBack = onBack
    )
  )
}
