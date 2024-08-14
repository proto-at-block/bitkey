package build.wallet.statemachine.pricechart

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.BitcoinPriceChartScreenId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.pricechart.*
import build.wallet.pricechart.DataPoint
import build.wallet.statemachine.core.ScreenModel
import build.wallet.time.DateTimeFormatter
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.milliseconds

class BitcoinPriceChartUiStateMachineImpl(
  private val haptics: Haptics,
  private val eventTracker: EventTracker,
  private val dateTimeFormatter: DateTimeFormatter,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val chartDataFetcher: ChartDataFetcherService,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val currencyConverter: CurrencyConverter,
) : BitcoinPriceChartUiStateMachine {
  @Composable
  override fun model(props: BitcoinPriceChartUiProps): ScreenModel {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    var data by remember { mutableStateOf<ImmutableList<DataPoint>>(emptyImmutableList()) }
    var isLoading by remember { mutableStateOf(false) }
    var failedToLoad by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(props.initialType) }
    var selectedHistory by remember { mutableStateOf(ChartHistory.DAY) }
    var selectedPoint by remember { mutableStateOf<DataPoint?>(null) }
    var selectedPointPeriodText by remember { mutableStateOf<String?>(null) }
    var placeholderPointText by remember { mutableStateOf<String?>(null) }
    var placeholderPointDiffText by remember { mutableStateOf<String?>(null) }
    val priceDirection = remember { mutableStateOf(PriceDirection.STABLE) }
    val latestExchangeRate by remember {
      currencyConverter.convert(BitcoinMoney.btc(1.0), fiatCurrency, atTime = null)
    }.collectAsState(null)
    val selectedPointTimeText by remember {
      derivedStateOf {
        selectedPoint?.first?.let { timestamp ->
          formatSelectedTimestamp(timestamp, selectedHistory)
        }
      }
    }
    val selectedPointText by remember {
      derivedStateOf {
        // format the selected point or the latest value when not selected
        placeholderPointText ?: run {
          val pointValue = selectedPoint?.second?.toBigDecimal() ?: latestExchangeRate?.value
          pointValue?.let { value ->
            moneyDisplayFormatter.format(FiatMoney(fiatCurrency, value))
          }
        }
      }
    }
    val selectedPointDiffText by remember {
      derivedStateOf {
        placeholderPointDiffText ?: run {
          val diffMin = data.firstOrNull()?.second
          val diffMax = selectedPoint?.second ?: latestExchangeRate?.value?.doubleValue(false)
          formatSelectedDiffText(diffMin, diffMax, priceDirection)
        }
      }
    }
    LaunchedEffect(selectedHistory) {
      // reset to loading state
      isLoading = true
      failedToLoad = false
      placeholderPointText = selectedPointText
      placeholderPointDiffText = selectedPointDiffText
      data = emptyImmutableList()
      // debounce data loading
      delay(250.milliseconds)
      chartDataFetcher.getChartData(
        fullAccountId = props.fullAccountId,
        f8eEnvironment = props.f8eEnvironment,
        chartHistory = selectedHistory
      ).onSuccess { chartData ->
        isLoading = false
        selectedPointPeriodText = getString(selectedHistory.diffLabel)
        data = chartData.toImmutableList()
      }.onFailure {
        failedToLoad = true
      }
      // clear data change placeholders
      placeholderPointText = null
      placeholderPointDiffText = null
    }
    // track the current and previous selection provide haptics when selecting/deselecting
    var previouslySelectedPoint by remember { mutableStateOf<DataPoint?>(null) }
    LaunchedEffect(selectedPoint) {
      val selectionStartedOrStopped =
        (selectedPoint == null && previouslySelectedPoint != null) ||
          (selectedPoint != null && previouslySelectedPoint == null)
      if (selectionStartedOrStopped) {
        launch { haptics.vibrate(HapticsEffect.MediumClick) }
      } else if (selectedPoint != null && previouslySelectedPoint != null) {
        launch { haptics.vibrate(HapticsEffect.Selection) }
      }

      previouslySelectedPoint = selectedPoint
    }
    LaunchedEffect(selectedType) {
      eventTracker.track(
        EventTrackerScreenInfo(
          eventTrackerScreenId = when (selectedType) {
            ChartType.BTC_PRICE -> BitcoinPriceChartScreenId.BITCOIN_PRICE_HISTORY
            ChartType.BALANCE -> BitcoinPriceChartScreenId.BALANCE_HISTORY
          }
        )
      )
    }
    return ScreenModel(
      body = BitcoinPriceDetailsBodyModel(
        data = data,
        history = selectedHistory,
        type = selectedType,
        isLoading = isLoading,
        selectedPoint = selectedPoint,
        selectedPointPrimaryText = selectedPointText,
        selectedPointSecondaryText = selectedPointDiffText,
        selectedPointPeriodText = selectedPointPeriodText,
        selectedPointChartText = selectedPointTimeText,
        selectedPriceDirection = priceDirection.value,
        failedToLoad = failedToLoad,
        formatFiatValue = {
          moneyDisplayFormatter.formatCompact(
            FiatMoney(fiatCurrency, it.toBigDecimal().scale(0))
          )
        },
        onChartTypeSelected = { selectedType = it },
        onChartHistorySelected = { selectedHistory = it },
        onPointSelected = { selectedPoint = it },
        onBack = props.onBack
      )
    )
  }

  private fun formatSelectedDiffText(
    start: Double?,
    end: Double?,
    priceDirection: MutableState<PriceDirection>,
  ): String? {
    return if (end == null || start == null) {
      null
    } else {
      val diffPercent = (end - start) / start * 100
      val diffDecimal = BigDecimal.fromDouble(diffPercent, DecimalMode.US_CURRENCY)
      priceDirection.value = when {
        diffDecimal == BigDecimal.ZERO -> PriceDirection.STABLE
        diffDecimal.isPositive -> PriceDirection.UP
        else -> PriceDirection.DOWN
      }
      "${diffDecimal.abs().toPlainString()}%"
    }
  }

  /**
   * Format the [timestamp] to local format with a format based on the [selectedHistory].
   */
  private fun formatSelectedTimestamp(
    timestamp: Long,
    selectedHistory: ChartHistory,
  ): String {
    val timeZone = TimeZone.currentSystemDefault()
    val currentDateTime = Clock.System.now().toLocalDateTime(timeZone)
    val datetime = Instant.fromEpochSeconds(timestamp).toLocalDateTime(timeZone)
    return when (selectedHistory) {
      ChartHistory.DAY -> {
        if (datetime.dayOfYear == currentDateTime.dayOfYear) {
          "Today ${dateTimeFormatter.localTime(datetime)}"
        } else {
          "Yesterday ${dateTimeFormatter.localTime(datetime)}"
        }
      }
      ChartHistory.WEEK -> {
        val weekDayName = datetime.dayOfWeek.name
          .lowercase()
          .replaceFirstChar { it.uppercaseChar() }
        "$weekDayName ${dateTimeFormatter.localTime(datetime)}"
      }
      ChartHistory.MONTH -> {
        dateTimeFormatter.shortDateWithTime(datetime)
      }
      ChartHistory.YEAR,
      ChartHistory.ALL,
      -> dateTimeFormatter.longLocalDate(datetime.date)
    }
  }
}
