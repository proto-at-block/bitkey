package build.wallet.statemachine.pricechart

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.BitcoinPriceChartScreenId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.pricechart.*
import build.wallet.statemachine.core.ScreenModel
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.milliseconds

@BitkeyInject(ActivityScope::class)
class BitcoinPriceChartUiStateMachineImpl(
  private val clock: Clock,
  private val haptics: Haptics,
  private val eventTracker: EventTracker,
  private val timeZoneProvider: TimeZoneProvider,
  private val dateTimeFormatter: DateTimeFormatter,
  private val currencyConverter: CurrencyConverter,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val chartDataFetcherService: ChartDataFetcherService,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : BitcoinPriceChartUiStateMachine {
  @Composable
  override fun model(props: BitcoinPriceChartUiProps): ScreenModel {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    var data by remember { mutableStateOf<ImmutableList<DataPoint>>(emptyImmutableList()) }
    var isLoading by remember { mutableStateOf(true) }
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
          formatSelectedTimestamp(clock, timeZoneProvider, timestamp, selectedHistory)
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
      // clear data and reset state for new data range
      failedToLoad = false
      data = emptyImmutableList()
      placeholderPointText = selectedPointText
      placeholderPointDiffText = selectedPointDiffText
    }
    LaunchedEffect(selectedHistory, fiatCurrency, latestExchangeRate) {
      if (placeholderPointText != null) {
        // debounce data loading when changing selected history
        delay(250.milliseconds)
      }

      chartDataFetcherService.getChartData(
        accountId = props.accountId,
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
        onChartHistorySelected = {
          if (selectedHistory != it) {
            isLoading = true
            selectedHistory = it
          }
        },
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
    if (end == null || start == null) return null
    val diffPercent = (end - start) / start * 100
    val diffDecimal = BigDecimal.fromDouble(diffPercent, DecimalMode.US_CURRENCY)
    priceDirection.value = PriceDirection.from(diffDecimal)
    return "${diffDecimal.abs().toPlainString()}%"
  }

  /**
   * Format the [timestamp] to local format with a format based on the [selectedHistory].
   */
  private fun formatSelectedTimestamp(
    clock: Clock,
    timeZoneProvider: TimeZoneProvider,
    timestamp: Long,
    selectedHistory: ChartHistory,
  ): String {
    val timeZone = timeZoneProvider.current()
    val currentDateTime = clock.now().toLocalDateTime(timeZone)
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
