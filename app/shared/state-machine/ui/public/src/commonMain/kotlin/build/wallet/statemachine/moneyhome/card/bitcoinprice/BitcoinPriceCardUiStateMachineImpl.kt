package build.wallet.statemachine.moneyhome.card.bitcoinprice

import androidx.compose.runtime.*
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.BTC
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.pricechart.*
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.toLocalDateTime

private const val SPARKLINE_MAX_POINTS = 50

class BitcoinPriceCardUiStateMachineImpl(
  private val timeZoneProvider: TimeZoneProvider,
  private val bitcoinPriceCardPreference: BitcoinPriceCardPreference,
  private val dateTimeFormatter: DateTimeFormatter,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val chartDataFetcherService: ChartDataFetcherService,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val currencyConverter: CurrencyConverter,
) : BitcoinPriceCardUiStateMachine {
  @Composable
  override fun model(props: BitcoinPriceCardUiProps): CardModel? {
    val enabled by remember { bitcoinPriceCardPreference.isEnabled }.collectAsState()
    if (!enabled) {
      return null
    }
    var data by remember { mutableStateOf<ImmutableList<DataPoint>>(emptyImmutableList()) }
    var isLoading by remember { mutableStateOf(true) }
    val fiatCurrency by remember {
      fiatCurrencyPreferenceRepository.fiatCurrencyPreference
    }.collectAsState()
    val lastUpdated by remember {
      currencyConverter.latestRateTimestamp(BTC, fiatCurrency)
        .filterNotNull()
        .map { updateTime ->
          val localTime = updateTime.toLocalDateTime(timeZoneProvider.current())
          "Updated ${dateTimeFormatter.localTime(localTime)}"
        }
    }.collectAsState("")
    val priceMoney by remember {
      val btc = BitcoinMoney.btc(1.0)
      currencyConverter.convert(btc, fiatCurrency, null)
    }.collectAsState(null)
    val price by remember {
      derivedStateOf {
        priceMoney?.let(moneyDisplayFormatter::format).orEmpty()
      }
    }
    val priceDirection = remember { mutableStateOf(PriceDirection.STABLE) }
    val priceChange by produceState("0% today", priceMoney, data) {
      val end = priceMoney?.value?.doubleValue(exactRequired = false) ?: return@produceState
      val start = data.firstOrNull()?.second ?: return@produceState
      val diffPercent = (end - start) / start * 100
      val diffDecimal = BigDecimal.fromDouble(diffPercent, DecimalMode.US_CURRENCY)
      priceDirection.value = PriceDirection.from(diffDecimal)
      value = "${diffDecimal.abs().toPlainString()}% today"
      isLoading = false
    }
    LaunchedEffect(priceMoney, fiatCurrency) {
      if (priceMoney != null) {
        chartDataFetcherService.getChartData(
          fullAccountId = props.fullAccountId,
          f8eEnvironment = props.f8eEnvironment,
          chartHistory = ChartHistory.DAY,
          maxPricePoints = SPARKLINE_MAX_POINTS
        ).onSuccess { chartData ->
          data = chartData.toImmutableList()
        }
      }
    }

    return CardModel(
      title = null,
      content = CardModel.CardContent.BitcoinPrice(
        data = data,
        price = price,
        priceChange = priceChange,
        priceDirection = priceDirection.value,
        lastUpdated = lastUpdated,
        isLoading = isLoading
      ),
      onClick = props.onOpenPriceChart,
      style = CardModel.CardStyle.Outline
    )
  }
}
