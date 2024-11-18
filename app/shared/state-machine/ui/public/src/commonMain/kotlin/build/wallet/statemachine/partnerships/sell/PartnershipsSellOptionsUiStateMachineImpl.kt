package build.wallet.statemachine.partnerships.sell

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.SellEventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.partnerships.GetSaleQuoteListF8eClient
import build.wallet.f8e.partnerships.GetSellRedirectF8eClient
import build.wallet.f8e.partnerships.RedirectInfo
import build.wallet.f8e.partnerships.RedirectUrlType
import build.wallet.feature.flags.SellBitcoinQuotesEnabledFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.ktor.result.NetworkingError
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.partnerships.*
import build.wallet.platform.links.AppRestrictions
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.Icon.Bitcoin
import build.wallet.statemachine.core.Icon.SmallIconCaretRight
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import build.wallet.statemachine.partnerships.PartnershipsSegment
import build.wallet.statemachine.partnerships.sell.PartnershipsSellState.QuotesState
import build.wallet.statemachine.partnerships.sell.PartnershipsSellState.RedirectState
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconImage.UrlImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_ITEM
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.list.ListItemTreatment.PRIMARY
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

class PartnershipsSellOptionsUiStateMachineImpl(
  private val getSaleQuoteListF8eClient: GetSaleQuoteListF8eClient,
  private val getSellRedirectF8eClient: GetSellRedirectF8eClient,
  private val partnershipTransactionsService: PartnershipTransactionsService,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val currencyConverter: CurrencyConverter,
  private val eventTracker: EventTracker,
  private val moneyFormatter: MoneyDisplayFormatter,
  private val sellBitcoinQuotesEnabledFeatureFlag: SellBitcoinQuotesEnabledFeatureFlag,
) : PartnershipsSellOptionsUiStateMachine {
  @Composable
  override fun model(props: PartnershipsSellOptionsUiProps): ScreenModel {
    var state: PartnershipsSellState by remember { mutableStateOf(QuotesState.LoadingPartnershipsSell) }
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    val formattedSellAmount by remember {
      when (props.exchangeRates) {
        null -> mutableStateOf(moneyFormatter.format(props.sellAmount))
        else -> {
          currencyConverter.convert(
            fromAmount = props.sellAmount,
            toCurrency = fiatCurrency,
            rates = props.exchangeRates
          )?.let {
            mutableStateOf(moneyFormatter.format(it))
          } ?: mutableStateOf(moneyFormatter.format(props.sellAmount))
        }
      }
    }

    return when (val currentState = state) {
      is QuotesState.LoadingPartnershipsSell -> {
        LaunchedEffect("load-sell-partners") {
          getSaleQuoteListF8eClient
            .getSaleQuotes(
              fullAccountId = props.keybox.fullAccountId,
              f8eEnvironment = props.keybox.config.f8eEnvironment,
              cryptoAmount = props.sellAmount,
              fiatCurrency = fiatCurrency
            )
            .onSuccess {
              val sellQuotes = it.quotes.toImmutableList()

              sellQuotes.forEach { quote ->
                eventTracker.track(
                  action = Action.ACTION_APP_PARTNERSHIPS_VIEWED_SALE_PARTNER,
                  context = PartnerEventTrackerScreenIdContext(quote.partnerInfo)
                )
              }
              state =
                QuotesState.ChoosingPartnershipsSell(
                  quotes = it.quotes,
                  onPartnerSelected = { partner ->
                    state = RedirectState.Loading(
                      amount = props.sellAmount,
                      partner = partner
                    )
                  }
                )
            }
            .onFailure {
              state = QuotesState.LoadingPartnershipsSellFailure(
                error = it
              )
            }
        }
        return LoadingBodyModel(
          id = SellEventTrackerScreenId.LOADING_SELL_PARTNERS,
          onBack = props.onBack
        ).asModalFullScreen()
      }

      is QuotesState.LoadingPartnershipsSellFailure -> {
        return SellErrorModel(
          id = SellEventTrackerScreenId.SELL_PARTNERS_NOT_FOUND_ERROR,
          error = currentState.error,
          title = "Error loading sell partners",
          errorMessage = "There was an error loading sell partners. Please try again.",
          onBack = props.onBack
        )
      }
      is QuotesState.ChoosingPartnershipsSell -> {
        when {
          currentState.quotes.isEmpty() -> {
            return SellErrorModel(
              id = SellEventTrackerScreenId.SELL_PARTNERS_NOT_AVAILABLE,
              title = "Sell partners coming soon",
              errorMessage = "Selling isn’t available in your region yet. But we’re currently working with our partners and will send you an email when it’s ready to use.",
              onBack = props.onBack
            )
          }
          else -> if (sellBitcoinQuotesEnabledFeatureFlag.isEnabled()) {
            return ListSaleQuotesModel(
              formattedSellAmount,
              quotes = currentState.quotes,
              onSelectPartnerQuote = { currentState.onPartnerSelected(it.partnerInfo) },
              onBack = props.onBack
            )
          } else {
            return ListSellPartnersModel(
              id = SellEventTrackerScreenId.SELL_PARTNERS_LIST,
              props,
              partners = currentState.quotes.map { it.partnerInfo }.toImmutableList(),
              onPartnerSelected = currentState.onPartnerSelected
            )
          }
        }
      }
      is RedirectState.Loaded -> {
        handleRedirect(currentState, props)
        LoadingBodyModel(
          id = SellEventTrackerScreenId.SELL_PARTNER_REDIRECTING,
          eventTrackerContext = PartnerEventTrackerScreenIdContext(currentState.partner),
          onBack = props.onBack
        ).asModalFullScreen()
      }
      is RedirectState.Loading -> {
        LaunchedEffect("load-sale-partner-redirect-info") {
          coroutineBinding {
            val result = fetchRedirectInfo(props, currentState, fiatCurrency).bind()

            val localTransaction = partnershipTransactionsService.create(
              id = result.redirectInfo.partnerTransactionId,
              partnerInfo = currentState.partner,
              type = PartnershipTransactionType.SALE
            ).bind()

            localTransaction to result
          }.onFailure { error ->
            state =
              RedirectState.LoadingFailure(
                partner = currentState.partner,
                error = error
              )
          }.onSuccess { (transaction, result) ->
            state =
              RedirectState.Loaded(
                partner = currentState.partner,
                redirectInfo = result.redirectInfo,
                localTransaction = transaction
              )
          }
        }
        LoadingBodyModel(
          id = SellEventTrackerScreenId.LOADING_SELL_PARTNER_REDIRECT,
          eventTrackerContext = PartnerEventTrackerScreenIdContext(
            currentState.partner
          ),
          onBack = props.onBack
        ).asModalFullScreen()
      }
      is RedirectState.LoadingFailure -> {
        val partnerName = currentState.partner.name
        SellErrorModel(
          id = SellEventTrackerScreenId.SELL_PARTNER_REDIRECT_ERROR,
          error = currentState.error,
          title = "Error",
          errorMessage = "Failed to redirect to $partnerName.",
          onBack = props.onBack
        )
      }
    }
  }

  @Composable
  private fun ListSellPartnersModel(
    id: SellEventTrackerScreenId,
    props: PartnershipsSellOptionsUiProps,
    partners: ImmutableList<PartnerInfo>,
    onPartnerSelected: (PartnerInfo) -> Unit,
  ): ScreenModel {
    val partnerItems =
      partners.map { partner ->
        ListItemModel(
          leadingAccessory =
            IconAccessory(
              model =
                IconModel(
                  iconImage =
                    when (val url = partner.logoUrl) {
                      null -> LocalImage(Bitcoin)
                      else ->
                        UrlImage(
                          url = url,
                          fallbackIcon = Bitcoin
                        )
                    },
                  iconSize = IconSize.Regular
                )
            ),
          trailingAccessory = IconAccessory(
            model =
              IconModel(
                iconImage = LocalImage(SmallIconCaretRight),
                iconSize = IconSize.Small,
                iconTint = IconTint.On30
              )
          ),
          title = partner.name,
          onClick = { onPartnerSelected(partner) },
          treatment = PRIMARY
        )
      }.toMutableList()

    val comingSoonModel =
      ListItemModel(
        title = "More partners coming soon...",
        leadingAccessory =
          IconAccessory(
            model =
              IconModel(
                iconImage = LocalImage(Icon.SmallIconStar),
                iconSize = IconSize.Regular,
                iconTint = IconTint.On30
              )
          ),
        treatment = ListItemTreatment.SECONDARY
      )

    partnerItems.add(comingSoonModel)

    val partnersGroupModel =
      ListGroupModel(
        items = partnerItems.toImmutableList(),
        style = CARD_ITEM
      )
    return SellPartnersModel(
      id = id,
      content = ListGroup(listGroupModel = partnersGroupModel),
      onBack = props.onBack
    )
  }

  @Composable
  private fun SellPartnersModel(
    id: SellEventTrackerScreenId,
    content: FormMainContentModel,
    onBack: () -> Unit,
  ): ScreenModel {
    return ScreenModel(
      body = SellPartnersFormBodyModel(
        onBack = onBack,
        content = content,
        id = id
      ),
      presentationStyle = ScreenPresentationStyle.ModalFullScreen
    )
  }

  @Composable
  private fun SellErrorModel(
    id: SellEventTrackerScreenId,
    context: PartnerEventTrackerScreenIdContext? = null,
    title: String = "Error",
    error: Throwable? = null,
    errorMessage: String,
    onBack: () -> Unit,
  ): ScreenModel {
    val logLevel = if (error != null) LogLevel.Error else LogLevel.Warn
    log(level = logLevel, throwable = error) { errorMessage }
    return ScreenModel(
      body =
        ErrorFormBodyModel(
          eventTrackerScreenId = id,
          eventTrackerContext = context,
          title = title,
          subline = errorMessage,
          primaryButton = ButtonDataModel("Got it", isLoading = false, onClick = onBack),
          onBack = onBack,
          errorData = ErrorData(
            segment = PartnershipsSegment.Sell,
            actionDescription = "Loading sell partners",
            cause = error ?: Throwable(errorMessage)
          )
        ),
      presentationStyle = ScreenPresentationStyle.ModalFullScreen
    )
  }

  private suspend fun fetchRedirectInfo(
    props: PartnershipsSellOptionsUiProps,
    redirectLoadingState: RedirectState.Loading,
    fiatCurrency: FiatCurrency,
  ): Result<GetSellRedirectF8eClient.Success, Error> {
    val fiatAmount = currencyConverter.convert(
      fromAmount = redirectLoadingState.amount,
      toCurrency = fiatCurrency,
      rates = props.exchangeRates?.toImmutableList() ?: persistentListOf()
    ) as? FiatMoney

    return if (fiatAmount == null) {
      Err(Error("Failed to convert amount to fiat"))
    } else {
      getSellRedirectF8eClient.sellRedirect(
        fullAccountId = props.keybox.fullAccountId,
        f8eEnvironment = props.keybox.config.f8eEnvironment,
        fiatAmount = fiatAmount,
        bitcoinAmount = redirectLoadingState.amount,
        partner = redirectLoadingState.partner.partnerId.value
      )
    }
  }

  private fun handleRedirect(
    redirectLoadedState: RedirectState.Loaded,
    props: PartnershipsSellOptionsUiProps,
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

  @Composable
  private fun ListSaleQuotesModel(
    formattedSellAmount: String,
    quotes: ImmutableList<SaleQuote>,
    onSelectPartnerQuote: (SaleQuote) -> Unit,
    onBack: () -> Unit,
  ): ScreenModel {
    val models = quotes
      .sortedWith(
        compareByDescending { it.cryptoAmount }
      )
      .map { quote ->
        val bitcoinDisplayAmount = moneyFormatter.format(BitcoinMoney.btc(quote.cryptoAmount))
        val fiatDisplayAmount = moneyFormatter.format(FiatMoney(USD, quote.fiatAmount.toBigDecimal()))

        ListItemModel(
          title = quote.partnerInfo.name,
          sideText = fiatDisplayAmount,
          secondarySideText = bitcoinDisplayAmount,
          onClick = { onSelectPartnerQuote(quote) },
          leadingAccessory = IconAccessory(
            model = IconModel(
              iconImage =
                when (val url = quote.partnerInfo.logoUrl) {
                  null -> IconImage.LocalImage(Icon.Bitcoin)
                  else ->
                    IconImage.UrlImage(
                      url = url,
                      fallbackIcon = Icon.Bitcoin
                    )
                },
              iconSize = IconSize.Regular
            )
          ),
          trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30)
        )
      }.toImmutableList()

    return SellQuotesFormBodyModel(
      formattedSellAmount = formattedSellAmount,
      mainContentList = immutableListOf(ListGroup(listGroupModel = ListGroupModel(items = models, style = CARD_ITEM))),
      id = SellEventTrackerScreenId.SELL_QUOTES_LIST,
      onBack = onBack
    ).asModalFullScreen()
  }
}

private class SellPartnersFormBodyModel(
  private val content: FormMainContentModel,
  override val id: SellEventTrackerScreenId,
  override val onBack: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory { onBack() }
    ),
    header = FormHeaderModel(
      headline = "Sell",
      subline = "Select a partner to sell bitcoin."
    ),
    mainContentList = immutableListOf(content),
    primaryButton = null,
    id = id,
    renderContext = Screen
  )

class SellQuotesFormBodyModel(
  private val formattedSellAmount: String,
  override val mainContentList: ImmutableList<FormMainContentModel>,
  override val id: SellEventTrackerScreenId,
  override val onBack: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory { onBack() }
    ),
    header = FormHeaderModel(
      headline = "Sell $formattedSellAmount",
      subline = "Offers show estimates of the amount you'll receive after exchange fees. Bitkey does not charge a fee."
    ),
    mainContentList = mainContentList,
    primaryButton = null,
    id = id,
    renderContext = Screen
  )

private sealed interface PartnershipsSellState {
  sealed interface QuotesState : PartnershipsSellState {
    /**
     * Loading available sell partners
     */
    data object LoadingPartnershipsSell : PartnershipsSellState

    /**
     * There was a failure loading sell partners
     */
    data class LoadingPartnershipsSellFailure(
      val error: NetworkingError,
    ) : PartnershipsSellState

    /**
     * Choosing a sell partner
     */
    data class ChoosingPartnershipsSell(
      val quotes: ImmutableList<SaleQuote>,
      val onPartnerSelected: (PartnerInfo) -> Unit,
    ) : PartnershipsSellState
  }

  /**
   * Describes state of the data used for the partner redirects
   */
  sealed interface RedirectState : PartnershipsSellState {
    /**
     * Loading partner redirect info
     * @param amount - amount to be sold
     * @param partner - partner to use for the sell
     */
    data class Loading(
      val amount: BitcoinMoney,
      val partner: PartnerInfo,
    ) : RedirectState

    /**
     * Partner redirect info loaded
     * @param partner - partner to sell from
     * @param redirectInfo - redirect info for the partner
     */
    data class Loaded(
      val partner: PartnerInfo,
      val redirectInfo: RedirectInfo,
      val localTransaction: PartnershipTransaction,
    ) : RedirectState

    /**
     * Failure in loading partner redirect info
     * @param partner - partner to sell from
     * @param error - error that occurred
     */
    data class LoadingFailure(
      val partner: PartnerInfo,
      val error: Throwable,
    ) : RedirectState
  }
}
