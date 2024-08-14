package build.wallet.statemachine.partnerships.purchase

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.partnerships.*
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateSyncer
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.partnerships.*
import build.wallet.platform.links.AppRestrictions
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize.MIN40
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseState.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlin.contracts.contract

// TODO: W-5675 - defaulting to card for now, but will eventually support other payment methods
private const val SUPPORTED_PAYMENT_METHOD = "CARD"
private const val MAX_DISPLAY_OPTIONS = 5

class PartnershipsPurchaseUiStateMachineImpl(
  val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val getPurchaseOptionsF8eClient: GetPurchaseOptionsF8eClient,
  private val getPurchaseQuoteListF8eClient: GetPurchaseQuoteListF8eClient,
  private val getPurchaseRedirectF8eClient: GetPurchaseRedirectF8eClient,
  private val partnershipsRepository: PartnershipTransactionsStatusRepository,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val eventTracker: EventTracker,
  private val exchangeRateSyncer: ExchangeRateSyncer,
  private val currencyConverter: CurrencyConverter,
  private val bitcoinAddressService: BitcoinAddressService,
) : PartnershipsPurchaseUiStateMachine {
  @Composable
  override fun model(props: PartnershipsPurchaseUiProps): SheetModel {
    val initialState = PurchaseAmountsState.Loading(preSelectedAmount = null)
    var state: PartnershipsPurchaseState by remember {
      mutableStateOf(PurchaseAmountsState.Loading(preSelectedAmount = props.selectedAmount))
    }
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    val exchangeRates: ImmutableList<ExchangeRate> by remember {
      mutableStateOf(exchangeRateSyncer.exchangeRates.value.toImmutableList())
    }
    return when (val currentState = state) {
      is PurchaseAmountsState.Loaded -> {
        return selectPurchaseAmountModel(
          purchaseAmounts = currentState.purchaseAmounts,
          selectedAmount = currentState.selectedAmount,
          moneyDisplayFormatter = moneyDisplayFormatter,
          onSelectAmount = { amount ->
            // deselect amount if it's already selected
            val selectedAmount = amount.takeIf { currentState.selectedAmount != amount }
            state =
              PurchaseAmountsState.Loaded(
                minAmount = currentState.minAmount,
                maxAmount = currentState.maxAmount,
                purchaseAmounts = currentState.purchaseAmounts,
                selectedAmount = selectedAmount
              )
          },
          onSelectCustomAmount = {
            props.onSelectCustomAmount(currentState.minAmount, currentState.maxAmount)
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
          purchaseMethodAmounts(props, fiatCurrency)
            .onFailure { error ->
              state = PurchaseAmountsState.LoadingFailure(error)
            }
            .onSuccess {
              state =
                if (isValidPurchaseAmount(currentState.preSelectedAmount, fiatCurrency, it.min, it.max)) {
                  QuotesState.Loading(currentState.preSelectedAmount)
                } else {
                  val displayOptions = it.displayOptions.take(MAX_DISPLAY_OPTIONS).toImmutableList()
                  PurchaseAmountsState.Loaded(it.min, it.max, displayOptions, it.default)
                }
            }
        }
        loadingModel(
          id = DepositEventTrackerScreenId.LOADING_PARTNER_PURCHASE_OPTIONS,
          onExit = props.onExit
        )
      }

      is QuotesState.Loaded -> {
        currentState.quotes.forEach { quote ->
          eventTracker.track(
            action = Action.ACTION_APP_PARTNERSHIPS_VIEWED_PURCHASE_QUOTE,
            context = PartnerEventTrackerScreenIdContext(quote.partnerInfo)
          )
        }
        return selectPartnerQuoteModel(
          title = "Purchase ${moneyDisplayFormatter.format(currentState.amount)}",
          subTitle = "Offers show amount you'll receive after exchange fees. Bitkey does not charge a fee.",
          quotes = currentState.quotes.map {
            it.toQuoteDisplay(moneyDisplayFormatter, exchangeRates, currencyConverter)
          }.toImmutableList(),
          onSelectPartnerQuote = {
            state =
              RedirectState.Loading(
                amount = currentState.amount,
                quote = it,
                paymentMethod = SUPPORTED_PAYMENT_METHOD
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
          getPurchaseQuoteListF8eClient
            .purchaseQuotes(
              fullAccountId = props.keybox.fullAccountId,
              f8eEnvironment = props.keybox.config.f8eEnvironment,
              fiatAmount = currentState.amount,
              paymentMethod = SUPPORTED_PAYMENT_METHOD
            ).onFailure { error ->
              state = QuotesState.LoadingFailure(error = error)
            }.onSuccess {
              state =
                QuotesState.Loaded(
                  amount = currentState.amount,
                  paymentMethod = SUPPORTED_PAYMENT_METHOD,
                  quotes = it.quoteList.toImmutableList(),
                  previousPartnerIds = partnershipsRepository.previouslyUsedPartnerIds.first()
                )
            }
        }
        loadingModel(
          id = DepositEventTrackerScreenId.LOADING_PARTNER_QUOTES_LIST,
          onExit = props.onExit
        )
      }
      is RedirectState.Loaded -> {
        handleRedirect(currentState, props)
        loadingModel(
          id = DepositEventTrackerScreenId.PURCHASE_PARTNER_REDIRECTING,
          context = PartnerEventTrackerScreenIdContext(currentState.partner),
          onExit = props.onExit
        )
      }
      is RedirectState.Loading -> {
        LaunchedEffect("load-purchase-partner-redirect-info") {
          coroutineBinding {
            val localTransaction = partnershipsRepository.create(
              partnerInfo = currentState.quote.partnerInfo,
              type = PartnershipTransactionType.PURCHASE
            ).bind()

            localTransaction to fetchRedirectInfo(props, currentState, localTransaction.id).bind()
          }.onFailure { error ->
            state =
              RedirectState.LoadingFailure(
                partner = currentState.quote.partnerInfo,
                error = error
              )
          }.onSuccess { (transaction, result) ->
            state =
              RedirectState.Loaded(
                partner = currentState.quote.partnerInfo,
                redirectInfo = result.redirectInfo,
                localTransaction = transaction
              )
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

  private suspend fun fetchRedirectInfo(
    props: PartnershipsPurchaseUiProps,
    redirectLoadingState: RedirectState.Loading,
    localTransactionId: PartnershipTransactionId,
  ): Result<GetPurchaseRedirectF8eClient.Success, Throwable> =
    coroutineBinding {
      bitcoinAddressService.generateAddress(props.account)
        .flatMap { address ->
          getPurchaseRedirectF8eClient.purchaseRedirect(
            fullAccountId = props.keybox.fullAccountId,
            address = address,
            f8eEnvironment = props.keybox.config.f8eEnvironment,
            fiatAmount = redirectLoadingState.amount,
            partner = redirectLoadingState.quote.partnerInfo.partnerId.value,
            paymentMethod = redirectLoadingState.paymentMethod,
            quoteId = redirectLoadingState.quote.quoteId,
            partnerTransactionId = localTransactionId
          )
        }.bind()
    }

  private fun isValidPurchaseAmount(
    purchaseAmount: FiatMoney?,
    currency: FiatCurrency,
    minAmount: FiatMoney,
    maxAmount: FiatMoney,
  ): Boolean {
    contract {
      returns(true) implies (purchaseAmount != null)
    }

    if (purchaseAmount == null) return false

    return purchaseAmount.currency == currency && purchaseAmount.value in minAmount.value..maxAmount.value
  }

  private fun handleRedirect(
    redirectLoadedState: RedirectState.Loaded,
    props: PartnershipsPurchaseUiProps,
  ) {
    when (redirectLoadedState.redirectInfo.redirectType) {
      RedirectUrlType.DEEPLINK -> {
        props.onPartnerRedirected(
          PartnerRedirectionMethod.Deeplink(
            urlString = redirectLoadedState.redirectInfo.url,
            appRestrictions =
              redirectLoadedState.redirectInfo.appRestrictions?.let {
                AppRestrictions(
                  packageName = it.packageName,
                  minVersion = it.minVersion
                )
              },
            partnerName = redirectLoadedState.partner.name
          ),
          redirectLoadedState.localTransaction
        )
      }

      RedirectUrlType.WIDGET -> {
        props.onPartnerRedirected(
          PartnerRedirectionMethod.Web(
            urlString = redirectLoadedState.redirectInfo.url,
            partnerInfo = redirectLoadedState.partner
          ),
          redirectLoadedState.localTransaction
        )
      }
    }
  }

  private suspend fun purchaseMethodAmounts(
    props: PartnershipsPurchaseUiProps,
    fiatCurrency: FiatCurrency,
  ): Result<PurchaseMethodAmounts, Error> {
    return getPurchaseOptionsF8eClient
      .purchaseOptions(
        fullAccountId = props.keybox.fullAccountId,
        f8eEnvironment = props.keybox.config.f8eEnvironment,
        currency = fiatCurrency
      ).flatMap {
        it.toPurchaseMethodAmounts(fiatCurrency, SUPPORTED_PAYMENT_METHOD)
      }
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
  val logLevel = if (error != null) LogLevel.Error else LogLevel.Warn
  log(level = logLevel, throwable = error) { errorMessage }
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
    size = MIN40,
    dragIndicatorVisible = true
  )
}

@Composable
private fun loadingModel(
  id: DepositEventTrackerScreenId,
  context: PartnerEventTrackerScreenIdContext? = null,
  onExit: () -> Unit,
) = SheetModel(
  body =
    FormBodyModel(
      id = id,
      eventTrackerContext = context,
      onBack = {},
      toolbar = null,
      header = null,
      mainContentList = immutableListOf(Loader),
      primaryButton = null,
      renderContext = Sheet
    ),
  onClosed = onExit,
  size = MIN40,
  dragIndicatorVisible = true
)

/**
 * Describes state of the data used for the Partnerships purchase flow
 */
private sealed interface PartnershipsPurchaseState {
  sealed interface PurchaseAmountsState : PartnershipsPurchaseState {
    /**
     * Loading default purchase amounts used for partnerships
     *
     * @param preSelectedAmount - amount already selected by the user
     */
    data class Loading(val preSelectedAmount: FiatMoney?) : PurchaseAmountsState

    /**
     * Default purchase amounts have been loaded
     */
    data class Loaded(
      val minAmount: FiatMoney,
      val maxAmount: FiatMoney,
      val purchaseAmounts: ImmutableList<FiatMoney>,
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
      val paymentMethod: String,
      val quotes: ImmutableList<Quote>,
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
      val quote: Quote,
      val paymentMethod: String,
    ) : RedirectState

    /**
     * Partner redirect info loaded
     * @param partner - partner to purchase from
     * @param redirectInfo - redirect info for the partner
     */
    data class Loaded(
      val partner: PartnerInfo,
      val redirectInfo: RedirectInfo,
      val localTransaction: PartnershipTransaction,
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
