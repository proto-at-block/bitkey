package build.wallet.statemachine.partnerships.purchase

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.partnerships.GetPurchaseOptionsService
import build.wallet.f8e.partnerships.GetPurchaseQuoteListService
import build.wallet.f8e.partnerships.GetPurchaseRedirectService
import build.wallet.f8e.partnerships.PartnerInfo
import build.wallet.f8e.partnerships.PurchaseMethodAmounts
import build.wallet.f8e.partnerships.Quote
import build.wallet.f8e.partnerships.RedirectInfo
import build.wallet.f8e.partnerships.RedirectUrlType
import build.wallet.f8e.partnerships.toPurchaseMethodAmounts
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.platform.links.AppRestrictions
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize.MIN40
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.partnerships.PartnerRedirectionMethod
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseState.PurchaseAmountsState
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseState.QuotesState
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseState.RedirectState
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

// TODO: W-5675 - defaulting to card for now, but will eventually support other payment methods
private const val SUPPORTED_PAYMENT_METHOD = "CARD"
private const val MAX_DISPLAY_OPTIONS = 5

class PartnershipsPurchaseUiStateMachineImpl(
  val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val getPurchaseOptionsService: GetPurchaseOptionsService,
  private val getPurchaseQuoteListService: GetPurchaseQuoteListService,
  private val getPurchaseRedirectService: GetPurchaseRedirectService,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
) : PartnershipsPurchaseUiStateMachine {
  @Composable
  override fun model(props: PartnershipsPurchaseUiProps): SheetModel {
    var state: PartnershipsPurchaseState by remember {
      mutableStateOf(PurchaseAmountsState.Loading(preSelectedAmount = props.selectedAmount))
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
          onBack = props.onBack,
          onExit = props.onExit
        )
      }
      is PurchaseAmountsState.LoadingFailure ->
        failureModel(props, title = "Error", subline = "Failed to load purchase amounts.")
      is PurchaseAmountsState.Loading -> {
        LaunchedEffect("load-partnerships-purchase-amount") {
          purchaseMethodAmounts(props)
            .onFailure { error ->
              state =
                PurchaseAmountsState.LoadingFailure(
                  error = error,
                  retry = { state = PurchaseAmountsState.Loading(preSelectedAmount = null) }
                )
            }
            .onSuccess {
              state =
                if (isValidPurchaseAmount(props.selectedAmount, props.fiatCurrency, it.min, it.max)) {
                  QuotesState.Loading(props.selectedAmount)
                } else {
                  val displayOptions = it.displayOptions.take(MAX_DISPLAY_OPTIONS).toImmutableList()
                  PurchaseAmountsState.Loaded(it.min, it.max, displayOptions, it.default)
                }
            }
        }
        loadingModel(props)
      }

      is QuotesState.Loaded -> {
        return selectPartnerQuoteModel(
          title = "Choose a partner",
          subTitle = "Quotes include price and fees",
          moneyDisplayFormatter = moneyDisplayFormatter,
          quotes = currentState.quotes,
          onSelectPartnerQuote = {
            state =
              RedirectState.Loading(
                amount = currentState.amount,
                quote = it,
                paymentMethod = SUPPORTED_PAYMENT_METHOD
              )
          },
          onBack = props.onBack
        )
      }
      is QuotesState.LoadingFailure ->
        failureModel(props, title = "Error", subline = "Failed to load partner quotes.")
      is QuotesState.Loading -> {
        LaunchedEffect("load-partnerships-quotes") {
          getPurchaseQuoteListService
            .purchaseQuotes(
              fullAccountId = props.account.accountId,
              f8eEnvironment = props.account.config.f8eEnvironment,
              fiatAmount = currentState.amount,
              paymentMethod = SUPPORTED_PAYMENT_METHOD
            ).onFailure { error ->
              state =
                QuotesState.LoadingFailure(
                  error = error,
                  retry = { state = PurchaseAmountsState.Loading(preSelectedAmount = null) }
                )
            }.onSuccess {
              state =
                QuotesState.Loaded(
                  amount = currentState.amount,
                  paymentMethod = SUPPORTED_PAYMENT_METHOD,
                  quotes = it.quoteList.toImmutableList()
                )
            }
        }
        loadingModel(props)
      }
      is RedirectState.Loaded -> {
        handleRedirect(currentState, props)
        loadingModel(props)
      }
      is RedirectState.Loading -> {
        LaunchedEffect("load-purchase-partner-redirect-info") {
          fetchRedirectInfo(props, currentState).onFailure { error ->
            state =
              RedirectState.LoadingFailure(
                partner = currentState.quote.partnerInfo,
                error = error,
                retry = {
                  state = RedirectState.Loading(currentState.amount, currentState.quote, currentState.paymentMethod)
                }
              )
          }.onSuccess {
            state =
              RedirectState.Loaded(
                partner = currentState.quote.partnerInfo,
                redirectInfo = it.redirectInfo
              )
          }
        }
        loadingModel(props)
      }
      is RedirectState.LoadingFailure -> {
        val partnerName = currentState.partner.name
        failureModel(props, title = "Error", subline = "Failed to redirect to $partnerName.")
      }
    }
  }

  private suspend fun fetchRedirectInfo(
    props: PartnershipsPurchaseUiProps,
    redirectLoadingState: RedirectState.Loading,
  ): Result<GetPurchaseRedirectService.Success, Throwable> {
    return appSpendingWalletProvider.getSpendingWallet(props.account.keybox.activeSpendingKeyset)
      .flatMap { it.getNewAddress() }
      .flatMap { address ->
        getPurchaseRedirectService.purchaseRedirect(
          fullAccountId = props.account.accountId,
          address = address,
          f8eEnvironment = props.account.config.f8eEnvironment,
          fiatAmount = redirectLoadingState.amount,
          partner = redirectLoadingState.quote.partnerInfo.partner,
          paymentMethod = redirectLoadingState.paymentMethod,
          quoteId = redirectLoadingState.quote.quoteId
        )
      }
  }

  @OptIn(ExperimentalContracts::class)
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
          )
        )
      }

      RedirectUrlType.WIDGET -> {
        props.onPartnerRedirected(
          PartnerRedirectionMethod.Web(urlString = redirectLoadedState.redirectInfo.url)
        )
      }
    }
  }

  private suspend fun purchaseMethodAmounts(
    props: PartnershipsPurchaseUiProps,
  ): Result<PurchaseMethodAmounts, Error> {
    return getPurchaseOptionsService
      .purchaseOptions(
        fullAccountId = props.account.accountId,
        f8eEnvironment = props.account.config.f8eEnvironment,
        currency = props.fiatCurrency
      ).flatMap {
        it.toPurchaseMethodAmounts(props.fiatCurrency, SUPPORTED_PAYMENT_METHOD)
      }
  }
}

@Composable
private fun failureModel(
  props: PartnershipsPurchaseUiProps,
  title: String,
  subline: String,
) = SheetModel(
  body =
    ErrorFormBodyModel(
      eventTrackerScreenId = null,
      title = title,
      subline = subline,
      primaryButton = ButtonDataModel("Got it", isLoading = false, onClick = props.onBack),
      renderContext = Sheet,
      onBack = props.onBack
    ),
  onClosed = props.onExit,
  size = MIN40,
  dragIndicatorVisible = true
)

@Composable
private fun loadingModel(props: PartnershipsPurchaseUiProps) =
  SheetModel(
    body =
      FormBodyModel(
        id = null,
        onBack = props.onBack,
        toolbar = null,
        header = null,
        mainContentList = immutableListOf(Loader),
        primaryButton = null,
        renderContext = Sheet
      ),
    onClosed = props.onExit,
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
     * @param retry - will trigger the loading of purchase amounts again
     */
    data class LoadingFailure(
      val error: Error,
      val retry: () -> Unit,
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
    ) : QuotesState

    /**
     * Failure in loading quotes
     * @param retry - will trigger the loading of quotes again
     */
    data class LoadingFailure(
      val error: Error,
      val retry: () -> Unit,
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
    ) : RedirectState

    /**
     * Failure in loading partner redirect info
     * @param partner - partner to purchase from
     * @param error - error that occurred
     * @param retry - will load quotes again
     */
    data class LoadingFailure(
      val partner: PartnerInfo,
      val error: Throwable,
      val retry: () -> Unit,
    ) : RedirectState
  }
}
