package build.wallet.statemachine.pricechart

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.BitcoinPriceChartScreenId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.BalanceHistoryFeatureFlag
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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
  private val balanceHistoryService: BalanceHistoryService,
  private val balanceHistoryFeatureFlag: BalanceHistoryFeatureFlag,
  private val timeScalePreference: ChartRangePreference,
) : BitcoinPriceChartUiStateMachine {
  @Composable
  override fun model(props: BitcoinPriceChartUiProps): ScreenModel {
    val balanceHistoryEnabled by remember {
      balanceHistoryFeatureFlag.flagValue()
        .map { it.value }
    }.collectAsState(initial = false)
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    var data by remember { mutableStateOf<ImmutableList<DataPoint>>(emptyImmutableList()) }
    var isLoading by remember { mutableStateOf(true) }
    var failedToLoad by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(props.initialType) }
    val timeScalePreference by remember { timeScalePreference.selectedRange }.collectAsState()
    var selectedRange by remember { mutableStateOf(timeScalePreference) }
    var selectedPoint by remember { mutableStateOf<DataPoint?>(null) }
    var selectedPointData by remember { mutableStateOf<SelectedPointData?>(null) }
    var selectedPointPeriodText by remember { mutableStateOf<String?>(null) }
    var placeholderPointData by remember { mutableStateOf<SelectedPointData?>(null) }
    val priceDirection = remember { mutableStateOf(PriceDirection.STABLE) }
    val latestExchangeRateFlow = remember {
      currencyConverter.convert(BitcoinMoney.btc(1.0), fiatCurrency, atTime = null)
    }
    val latestExchangeRate by latestExchangeRateFlow.collectAsState(null)
    val selectedPointTimeText by remember {
      derivedStateOf {
        selectedPoint?.x?.let { timestamp ->
          formatSelectedTimestamp(timestamp, selectedRange)
        }
      }
    }
    GenerateHapticFeedback(selectedPoint)
    TrackTypeChangeEvent(selectedType)
    LaunchedEffect(selectedRange) {
      // clear data and reset state for new data range
      failedToLoad = false
      data = emptyImmutableList()
    }
    LaunchedEffect(selectedType, selectedRange, fiatCurrency) {
      if (placeholderPointData != null && selectedType == ChartType.BTC_PRICE) {
        // debounce data loading when changing selected history
        delay(250.milliseconds)
      }

      when (selectedType) {
        ChartType.BTC_PRICE ->
          latestExchangeRateFlow
            .map { chartDataFetcherService.getChartData(selectedRange) }
        ChartType.BALANCE ->
          balanceHistoryService.observe(selectedRange)
            .filter { it.isOk && it.value.size > 1 }
      }.onEach { result ->
        result
          .onSuccess { chartData ->
            selectedPointPeriodText = getString(selectedRange.diffLabel)
            data = chartData.toImmutableList()
          }
          .onFailure {
            failedToLoad = true
          }
        isLoading = false
        placeholderPointData = null
      }.launchIn(this)
    }
    val selectedRangeLabel = stringResource(selectedRange.diffLabel)
    LaunchedEffect(data, selectedType, selectedRange, selectedPoint) {
      val selectedYValue = selectedPoint?.y?.toBigDecimal()
      if (isLoading) return@LaunchedEffect
      selectedPointData = when (selectedType) {
        ChartType.BTC_PRICE -> {
          val pointValue = selectedYValue ?: latestExchangeRate?.value
          SelectedPointData.BtcPrice(
            isUserSelected = selectedPoint != null,
            primaryText = pointValue?.let { value ->
              moneyDisplayFormatter.format(FiatMoney(fiatCurrency, value))
            }.orEmpty(),
            secondaryText = formatSelectedDiffText(
              data.firstOrNull()?.y,
              pointValue?.doubleValue(false),
              priceDirection
            ).orEmpty(),
            secondaryTimePeriodText = selectedPointPeriodText.orEmpty(),
            direction = priceDirection.value
          )
        }
        ChartType.BALANCE -> {
          val selectedBalanceAt = selectedPoint as? BalanceAt
          val startPoint = data.firstOrNull { it.y > 0.0 } as? BalanceAt
          val endPoint = (selectedPoint ?: data.lastOrNull()) as? BalanceAt
          val lastPoint = data.lastOrNull() as? BalanceAt
          SelectedPointData.Balance(
            isUserSelected = selectedPoint != null,
            primaryFiatText = (selectedBalanceAt ?: lastPoint)?.run {
              moneyDisplayFormatter.format(FiatMoney(fiatCurrency, fiatBalance.toBigDecimal()))
            }.orEmpty(),
            secondaryFiatText = formatSelectedDiffText(
              startPoint?.fiatBalance,
              lastPoint?.fiatBalance
            )?.run { "$this $selectedRangeLabel" }
              .orEmpty(),
            primaryBtcText = endPoint
              ?.run { moneyDisplayFormatter.format(BitcoinMoney.btc(balance)) }
              .orEmpty(),
            secondaryBtcText = formatSelectedDiffText(
              startPoint?.balance,
              lastPoint?.balance
            )?.run { "$this $selectedRangeLabel" }
              .orEmpty()
          )
        }
      }
    }
    return ScreenModel(
      body = BitcoinPriceDetailsBodyModel(
        data = data,
        range = selectedRange,
        type = selectedType,
        isLoading = isLoading,
        isBalanceHistoryEnabled = balanceHistoryEnabled,
        selectedPoint = selectedPoint,
        selectedPointData = selectedPointData,
        selectedPointTimestamp = selectedPointTimeText,
        failedToLoad = failedToLoad,
        fiatCurrencyCode = fiatCurrency.textCode.code,
        onBuy = props.onBuy,
        onTransfer = props.onTransfer,
        formatFiatValue = { value, precise ->
          formatValue(value, precise, fiatCurrency)
        },
        onChartTypeSelected = {
          if (selectedType != it) {
            placeholderPointData = selectedPointData
            isLoading = true
            selectedType = it
          }
        },
        onChartRangeSelected = {
          if (selectedRange != it) {
            placeholderPointData = selectedPointData
            isLoading = true
            selectedRange = it
          }
        },
        onPointSelected = { selectedPoint = it },
        onBack = props.onBack
      )
    )
  }

  private fun formatValue(
    value: Double,
    precise: Boolean,
    fiatCurrency: FiatCurrency,
  ): String {
    val format = if (precise) {
      moneyDisplayFormatter::format
    } else {
      moneyDisplayFormatter::formatCompact
    }
    return format(
      FiatMoney(
        currency = fiatCurrency,
        value = value.toBigDecimal()
      )
    )
  }

  @Composable
  private fun TrackTypeChangeEvent(selectedType: ChartType) {
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
  }

  /**
   * Track the current and previous selection provide haptics when selecting/deselecting
   */
  @Composable
  private fun GenerateHapticFeedback(selectedPoint: DataPoint?) {
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
  }

  private fun formatSelectedDiffText(
    start: Double?,
    end: Double?,
    priceDirection: MutableState<PriceDirection>? = null,
  ): String? {
    val diffDecimal = when {
      end == null -> return null
      start == null || start == 0.0 -> end
      else -> (end - start) / start * 100
    }.let { BigDecimal.fromDouble(it, DecimalMode.US_CURRENCY) }
    val prefix = when {
      priceDirection != null -> {
        priceDirection.value = PriceDirection.from(diffDecimal)
        ""
      }
      diffDecimal.isZero() -> ""
      else -> if (diffDecimal.isPositive) "+" else "-"
    }
    return "$prefix${diffDecimal.abs().toPlainString()}%"
  }

  /**
   * Format the [timestamp] to local format with a format based on the [selectedHistory].
   */
  private fun formatSelectedTimestamp(
    timestamp: Long,
    selectedHistory: ChartRange,
  ): String {
    val timeZone = timeZoneProvider.current()
    val currentDateTime = clock.now().toLocalDateTime(timeZone)
    val datetime = Instant.fromEpochSeconds(timestamp).toLocalDateTime(timeZone)
    return when (selectedHistory) {
      ChartRange.DAY -> {
        if (datetime.dayOfYear == currentDateTime.dayOfYear) {
          "Today ${dateTimeFormatter.localTime(datetime)}"
        } else {
          "Yesterday ${dateTimeFormatter.localTime(datetime)}"
        }
      }
      ChartRange.WEEK -> {
        val weekDayName = datetime.dayOfWeek.name
          .lowercase()
          .replaceFirstChar { it.uppercaseChar() }
        "$weekDayName ${dateTimeFormatter.localTime(datetime)}"
      }
      ChartRange.MONTH -> dateTimeFormatter.shortDateWithTime(datetime)
      ChartRange.YEAR,
      ChartRange.ALL,
      -> dateTimeFormatter.longLocalDate(datetime.date)
    }
  }
}
