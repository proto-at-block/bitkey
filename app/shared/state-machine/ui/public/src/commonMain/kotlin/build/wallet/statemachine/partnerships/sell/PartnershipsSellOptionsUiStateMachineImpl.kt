package build.wallet.statemachine.partnerships.sell

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.SellEventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.partnerships.*
import build.wallet.ktor.result.NetworkingError
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.partnerships.*
import build.wallet.platform.links.AppRestrictions
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import build.wallet.statemachine.partnerships.PartnershipsSegment
import build.wallet.statemachine.partnerships.sell.PartnershipsSellState.*
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconImage.UrlImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_ITEM
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.list.ListItemTreatment.PRIMARY
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class PartnershipsSellOptionsUiStateMachineImpl(
  private val getSaleQuoteListF8eClient: GetSaleQuoteListF8eClient,
  private val getSellRedirectF8eClient: GetSellRedirectF8eClient,
  private val partnershipsRepository: PartnershipTransactionsStatusRepository,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val eventTracker: EventTracker,
) : PartnershipsSellOptionsUiStateMachine {
  @Composable
  override fun model(props: PartnershipsSellOptionsUiProps): ScreenModel {
    var state: PartnershipsSellState by remember { mutableStateOf(QuotesState.LoadingPartnershipsSell) }
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    return when (val currentState = state) {
      is QuotesState.LoadingPartnershipsSell -> {
        LaunchedEffect("load-sell-partners") {
          getSaleQuoteListF8eClient
            .getSaleQuotes(
              fullAccountId = props.keybox.fullAccountId,
              f8eEnvironment = props.keybox.config.f8eEnvironment,
              // placeholder value until we have a UI to input the amount
              cryptoAmount = BitcoinMoney.btc(0.1),
              fiatCurrency = fiatCurrency
            )
            .onSuccess {
              val sellPartners = it.quotes.map { quote ->
                quote.partnerInfo
              }.toImmutableList()

              sellPartners.forEach { partner ->
                eventTracker.track(
                  action = Action.ACTION_APP_PARTNERSHIPS_VIEWED_SALE_PARTNER,
                  context = PartnerEventTrackerScreenIdContext(partner)
                )
              }
              state =
                QuotesState.ChoosingPartnershipsSell(
                  sellPartners = sellPartners,
                  onPartnerSelected = { partner ->
                    state = RedirectState.Loading(
                      amount = FiatMoney.zero(fiatCurrency),
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
        ).asRootScreen()
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
          currentState.sellPartners.isEmpty() -> {
            return SellErrorModel(
              id = SellEventTrackerScreenId.SELL_PARTNERS_NOT_AVAILABLE,
              title = "Sell partners coming soon",
              errorMessage = "Selling isn’t available in your region yet. But we’re currently working with our partners and will send you an email when it’s ready to use.",
              onBack = props.onBack
            )
          }
          else -> {
            return ListSellPartnersModel(
              id = SellEventTrackerScreenId.SELL_PARTNERS_LIST,
              props,
              currentState.sellPartners,
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
        ).asRootScreen()
      }
      is RedirectState.Loading -> {
        LaunchedEffect("load-sale-partner-redirect-info") {
          coroutineBinding {
            val result = fetchRedirectInfo(props, currentState).bind()

            val localTransaction = partnershipsRepository.create(
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
        ).asRootScreen()
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
                iconImage = LocalImage(SmallIconStar),
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
      )
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
        )
    )
  }

  private suspend fun fetchRedirectInfo(
    props: PartnershipsSellOptionsUiProps,
    redirectLoadingState: RedirectState.Loading,
  ): Result<GetSellRedirectF8eClient.Success, Throwable> =
    getSellRedirectF8eClient.sellRedirect(
      fullAccountId = props.keybox.fullAccountId,
      f8eEnvironment = props.keybox.config.f8eEnvironment,
      fiatAmount = redirectLoadingState.amount,
      partner = redirectLoadingState.partner.partnerId.value
    )

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
    renderContext = Sheet
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
      val sellPartners: ImmutableList<PartnerInfo>,
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
      val amount: FiatMoney,
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
