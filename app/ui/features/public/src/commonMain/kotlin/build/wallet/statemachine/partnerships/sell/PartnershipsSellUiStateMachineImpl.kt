package build.wallet.statemachine.partnerships.sell

import androidx.compose.runtime.*
import bitkey.metrics.MetricOutcome
import bitkey.metrics.MetricTrackerService
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.SellBitcoinMaxAmountFeatureFlag
import build.wallet.feature.flags.SellBitcoinMinAmountFeatureFlag
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipEvent
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.partnerships.sell.SellState.*
import build.wallet.statemachine.partnerships.sell.metrics.PartnershipSellConfirmationMetricDefinition
import build.wallet.statemachine.partnerships.sell.metrics.PartnershipSellMetricDefinition
import build.wallet.statemachine.send.TransferAmountEntryUiProps
import build.wallet.statemachine.send.TransferAmountEntryUiStateMachine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Duration.Companion.minutes
import build.wallet.statemachine.partnerships.sell.metrics.PartnershipSellConfirmationMetricDefinition.Variants.Partner as PartnershipSellConfirmationVariant
import build.wallet.statemachine.partnerships.sell.metrics.PartnershipSellMetricDefinition.Variants.Partner as PartnershipSellVariant

@BitkeyInject(ActivityScope::class)
class PartnershipsSellUiStateMachineImpl(
  private val partnershipsSellOptionsUiStateMachine: PartnershipsSellOptionsUiStateMachine,
  private val partnershipsSellConfirmationUiStateMachine:
    PartnershipsSellConfirmationUiStateMachine,
  private val transferAmountEntryUiStateMachine: TransferAmountEntryUiStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val sellBitcoinMinAmountFeatureFlag: SellBitcoinMinAmountFeatureFlag,
  private val sellBitcoinMaxAmountFeatureFlag: SellBitcoinMaxAmountFeatureFlag,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val exchangeRateService: ExchangeRateService,
  private val deepLinkHandler: DeepLinkHandler,
  private val metricTrackerService: MetricTrackerService,
) : PartnershipsSellUiStateMachine {
  @Composable
  override fun model(props: PartnershipsSellUiProps): ScreenModel {
    var state: SellState by remember {
      when {
        props.confirmedSale != null -> mutableStateOf(SellConfirmation(props.confirmedSale))
        else -> mutableStateOf(EnteringSellAmount)
      }
    }

    StartMetricEffect(props)

    val fiatCurrency by remember { fiatCurrencyPreferenceRepository.fiatCurrencyPreference }
      .collectAsState()

    // On initiating the sell flow, we grab and lock in the current exchange rates, so we use
    // the same rates over the duration of the flow. This is null when the exchange rates are not
    // available or are out of date due to the customer being offline or unable to communicate with f8e
    val exchangeRates: ImmutableList<ExchangeRate>? by remember {
      mutableStateOf(
        exchangeRateService.mostRecentRatesSinceDurationForCurrency(6.minutes, fiatCurrency)
          ?.toImmutableList()
      )
    }

    val initialAmount by remember(exchangeRates) {
      when (exchangeRates) {
        null -> mutableStateOf(BitcoinMoney.zero())
        else -> mutableStateOf(FiatMoney.zero(fiatCurrency))
      }
    }

    // this is defaulted to .1 BTC for now to preserve legacy behaviour
    var sellAmount by remember { mutableStateOf(BitcoinMoney.btc(0.1)) }

    return when (val currentState = state) {
      is EnteringSellAmount -> transferAmountEntryUiStateMachine.model(
        props = TransferAmountEntryUiProps(
          onBack = {
            metricTrackerService.completeMetric(
              metricDefinition = PartnershipSellMetricDefinition,
              outcome = MetricOutcome.UserCanceled
            )
            props.onBack()
          },
          initialAmount = initialAmount,
          exchangeRates = exchangeRates,
          minAmount = sellBitcoinMinAmountFeatureFlag.flagValue().value.let { BitcoinMoney.btc(it.value) },
          maxAmount = sellBitcoinMaxAmountFeatureFlag.flagValue().value.let { BitcoinMoney.btc(it.value) },
          allowSendAll = false,
          onContinueClick = { continueTransferParams ->
            sellAmount = when (continueTransferParams.sendAmount) {
              is BitcoinTransactionSendAmount.ExactAmount -> continueTransferParams.sendAmount.money
              BitcoinTransactionSendAmount.SendAll -> error("Send all not supported, this shouldn't be possible")
            }
            state = ListSellPartners
          }
        )
      )
      is ListSellPartners -> {
        partnershipsSellOptionsUiStateMachine.model(
          PartnershipsSellOptionsUiProps(
            sellAmount = sellAmount,
            exchangeRates = exchangeRates,
            onBack = {
              state = EnteringSellAmount
            },
            onPartnerRedirected = { method, transaction ->
              metricTrackerService.setVariant(
                metricDefinition = PartnershipSellMetricDefinition,
                variant = PartnershipSellVariant(transaction.partnerInfo.partnerId)
              )
              metricTrackerService.completeMetric(
                metricDefinition = PartnershipSellMetricDefinition,
                outcome = MetricOutcome.Succeeded
              )

              handlePartnerRedirected(
                props = props,
                method = method,
                transaction = transaction,
                setState = {
                  state = it
                }
              )
            }
          )
        )
      }

      is ShowingSellRedirect -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = currentState.urlString,
              onClose = currentState.onClose
            )
          }
        ).asModalScreen()
      }

      is SellConfirmation -> {
        partnershipsSellConfirmationUiStateMachine.model(
          PartnershipsSellConfirmationProps(
            confirmedPartnerSale = currentState.confirmedPartnerSale,
            onBack = {
              metricTrackerService.completeMetric(
                metricDefinition = PartnershipSellConfirmationMetricDefinition,
                outcome = MetricOutcome.UserCanceled
              )
              state = EnteringSellAmount
            },
            exchangeRates = emptyImmutableList(),
            onDone = { partnerInfo ->
              metricTrackerService.completeMetric(
                metricDefinition = PartnershipSellConfirmationMetricDefinition,
                outcome = MetricOutcome.Succeeded
              )
              state = ShowingSellSuccess(partnerInfo)
            }
          )
        )
      }

      is TrackSell -> {
        TODO("Show external tracking link in webview")
      }

      is ShowingSellSuccess -> SellBitcoinSuccessBodyModel(
        partnerInfo = currentState.partnerInfo,
        onBack = props.onBack
      ).asModalScreen()
    }
  }

  private fun handlePartnerRedirected(
    props: PartnershipsSellUiProps,
    method: PartnerRedirectionMethod,
    transaction: PartnershipTransaction,
    setState: (SellState) -> Unit,
  ) {
    when (method) {
      is PartnerRedirectionMethod.Web -> {
        setState(
          ShowingSellRedirect(
            urlString = method.urlString,
            onClose = {
              setState(
                SellConfirmation(
                  ConfirmedPartnerSale(
                    partner = transaction.partnerInfo.partnerId,
                    event = transaction.context?.let { PartnershipEvent(it) },
                    partnerTransactionId = transaction.id
                  )
                )
              )
            }
          )
        )
      }
      is PartnerRedirectionMethod.Deeplink -> {
        deepLinkHandler.openDeeplink(
          url = method.urlString,
          appRestrictions = null
        )
        props.onBack()
      }
    }
  }

  /**
   * Starts the appropriate metric tracker for this session of the state machine via [MetricTrackerService].
   *
   * We distinguish between two possible metrics - either preparing a sell up to its redirect to a
   * partnership, or after a sell showing the sell confirmation screen.
   */
  @Composable
  private fun StartMetricEffect(props: PartnershipsSellUiProps) {
    LaunchedEffect("start-partnership-sell-metric", props) {
      when {
        props.confirmedSale != null -> metricTrackerService.startMetric(
          metricDefinition = PartnershipSellConfirmationMetricDefinition,
          variant = props.confirmedSale.partner?.let {
            PartnershipSellConfirmationVariant(props.confirmedSale.partner)
          }
        )
        else -> {
          metricTrackerService.startMetric(
            metricDefinition = PartnershipSellMetricDefinition
          )
        }
      }
    }
  }
}

sealed interface SellState {
  data object ListSellPartners : SellState

  /**
   * Indicates that we are displaying an in-app browser for a sell redirect.
   *
   * @param urlString - url to kick off the in-app browser with.
   * @param onClose - callback fired when browser closes
   */
  data class ShowingSellRedirect(
    val urlString: String,
    val onClose: () -> Unit,
  ) : SellState

  data class SellConfirmation(
    val confirmedPartnerSale: ConfirmedPartnerSale,
  ) : SellState

  data object TrackSell : SellState

  data class ShowingSellSuccess(val partnerInfo: PartnerInfo?) : SellState

  data object EnteringSellAmount : SellState
}
