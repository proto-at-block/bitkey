package build.wallet.statemachine.pricechart

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.BitcoinPriceChartScreenId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
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
import build.wallet.statemachine.money.amount.toAnimatedAmountValue
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

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
  private val timeScalePreference: ChartRangePreference,
) : BitcoinPriceChartUiStateMachine {
  @Composable
  override fun model(props: BitcoinPriceChartUiProps): ScreenModel {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    var data by remember { mutableStateOf<ImmutableList<DataPoint>>(emptyImmutableList()) }
    var dataFiatCurrency by remember { mutableStateOf<FiatCurrency?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var preservePreviousChartWhileLoading by remember { mutableStateOf(false) }
    var failedToLoad by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(props.initialType) }
    val timeScalePreference by remember { timeScalePreference.selectedRange }.collectAsState()
    var selectedRange by remember { mutableStateOf(timeScalePreference) }
    var selectedPoint by remember { mutableStateOf<DataPoint?>(null) }
    val rangeCaches = remember(fiatCurrency) {
      ChartRangeCaches(
        btc = mutableStateMapOf(),
        balance = mutableStateMapOf()
      )
    }
    val prefetchScope = rememberStableCoroutineScope()
    val pointTickEvents = remember {
      MutableSharedFlow<DataPoint>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
      )
    }
    var selectedPointData by remember { mutableStateOf<SelectedPointData?>(null) }
    val latestExchangeRateFlow = remember {
      currencyConverter.convert(BitcoinMoney.btc(1.0), fiatCurrency, atTime = null)
    }
    val latestExchangeRate by latestExchangeRateFlow.collectAsState(null)
    val selectedRangeLabel = stringResource(selectedRange.diffLabel)
    val selectedPointTimeText by remember {
      derivedStateOf {
        selectedPoint?.x?.let { timestamp ->
          formatSelectedTimestamp(timestamp, selectedRange)
        }
      }
    }

    GenerateSelectionLifecycleHapticFeedback(selectedPoint)
    GenerateChartPointTickHapticFeedback(pointTickEvents, haptics)
    TrackTypeChangeEvent(selectedType)
    ObserveRequestedChartData(
      selectedType = selectedType,
      selectedRange = selectedRange,
      fiatCurrency = fiatCurrency,
      latestExchangeRateFlow = latestExchangeRateFlow,
      rangeCaches = rangeCaches,
      onDataLoaded = { immutableChartData, loadedFiatCurrency ->
        failedToLoad = false
        data = immutableChartData
        dataFiatCurrency = loadedFiatCurrency
        preservePreviousChartWhileLoading = false
      },
      onLoadFailed = { hasCachedData ->
        if (!hasCachedData) {
          failedToLoad = true
        }
        preservePreviousChartWhileLoading = false
      },
      onLoadingFinished = {
        isLoading = false
      }
    )
    PrefetchChartRanges(
      selectedType = selectedType,
      selectedRange = selectedRange,
      fiatCurrency = fiatCurrency,
      data = data,
      dataFiatCurrency = dataFiatCurrency,
      isLoading = isLoading,
      failedToLoad = failedToLoad,
      rangeCaches = rangeCaches,
      prefetchScope = prefetchScope,
      onSelectedRangePrefetched = { prefetchedData, prefetchedFiatCurrency ->
        data = prefetchedData
        dataFiatCurrency = prefetchedFiatCurrency
        failedToLoad = false
        preservePreviousChartWhileLoading = false
        isLoading = false
      }
    )
    UpdateSelectedPointData(
      data = data,
      dataFiatCurrency = dataFiatCurrency,
      selectedType = selectedType,
      selectedPoint = selectedPoint,
      isLoading = isLoading,
      latestExchangeRateValue = latestExchangeRate?.value,
      fiatCurrency = fiatCurrency,
      selectedRangeLabel = selectedRangeLabel,
      onSelectedPointDataChange = { selectedPointData = it }
    )
    return ScreenModel(
      body = BitcoinPriceDetailsBodyModel(
        data = data,
        range = selectedRange,
        type = selectedType,
        isLoading = isLoading,
        preservePreviousChartWhileLoading = preservePreviousChartWhileLoading,
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
          handleChartTypeSelected(
            nextType = it,
            currentType = selectedType,
            selectedRange = selectedRange,
            fiatCurrency = fiatCurrency,
            currentData = data,
            currentDataFiatCurrency = dataFiatCurrency,
            rangeCaches = rangeCaches,
            onDataChange = { updatedData -> data = updatedData },
            onDataFiatCurrencyChange = {
                updatedFiatCurrency ->
              dataFiatCurrency = updatedFiatCurrency
            },
            onLoadingChange = { loading -> isLoading = loading },
            onPreservePreviousChartWhileLoadingChange = {
                preserve ->
              preservePreviousChartWhileLoading = preserve
            },
            onFailedToLoadChange = { failed -> failedToLoad = failed },
            onSelectedPointChange = { point -> selectedPoint = point },
            onTypeChange = { type -> selectedType = type }
          )
        },
        onChartRangeSelected = {
          handleChartRangeSelected(
            nextRange = it,
            currentRange = selectedRange,
            selectedType = selectedType,
            fiatCurrency = fiatCurrency,
            currentData = data,
            currentDataFiatCurrency = dataFiatCurrency,
            rangeCaches = rangeCaches,
            onDataChange = { updatedData -> data = updatedData },
            onDataFiatCurrencyChange = {
                updatedFiatCurrency ->
              dataFiatCurrency = updatedFiatCurrency
            },
            onLoadingChange = { loading -> isLoading = loading },
            onPreservePreviousChartWhileLoadingChange = {
                preserve ->
              preservePreviousChartWhileLoading = preserve
            },
            onFailedToLoadChange = { failed -> failedToLoad = failed },
            onSelectedPointChange = { point -> selectedPoint = point },
            onRangeChange = { range -> selectedRange = range }
          )
        },
        onPointSelected = {
          selectedPoint = it
        },
        onDisplayedPointSelected = { point ->
          point?.let(pointTickEvents::tryEmit)
        },
        onBack = props.onBack
      )
    )
  }

  @Composable
  private fun ObserveRequestedChartData(
    selectedType: ChartType,
    selectedRange: ChartRange,
    fiatCurrency: FiatCurrency,
    latestExchangeRateFlow: Flow<*>,
    rangeCaches: ChartRangeCaches,
    onDataLoaded: (ImmutableList<DataPoint>, FiatCurrency) -> Unit,
    onLoadFailed: (hasCachedData: Boolean) -> Unit,
    onLoadingFinished: () -> Unit,
  ) {
    LaunchedEffect(selectedType, selectedRange, fiatCurrency) {
      val requestedType = selectedType
      val requestedRange = selectedRange
      val requestedFiatCurrency = fiatCurrency
      chartDataFlow(
        type = requestedType,
        range = requestedRange,
        latestExchangeRateFlow = latestExchangeRateFlow
      ).onEach { result ->
        result
          .onSuccess { chartData ->
            val immutableChartData = chartData.toImmutableDataPoints()
            rangeCaches.forType(requestedType)[requestedRange] = immutableChartData
            onDataLoaded(immutableChartData, requestedFiatCurrency)
          }
          .onFailure {
            onLoadFailed(rangeCaches.cachedData(requestedType, requestedRange) != null)
          }
        onLoadingFinished()
      }.launchIn(this)
    }
  }

  @Composable
  private fun PrefetchChartRanges(
    selectedType: ChartType,
    selectedRange: ChartRange,
    fiatCurrency: FiatCurrency,
    data: ImmutableList<DataPoint>,
    dataFiatCurrency: FiatCurrency?,
    isLoading: Boolean,
    failedToLoad: Boolean,
    rangeCaches: ChartRangeCaches,
    prefetchScope: CoroutineScope,
    onSelectedRangePrefetched: (ImmutableList<DataPoint>, FiatCurrency) -> Unit,
  ) {
    val latestSelectedRequest by rememberUpdatedState(
      ChartSelectionKey(
        type = selectedType,
        range = selectedRange,
        fiatCurrency = fiatCurrency
      )
    )
    val inFlightPrefetches = remember(fiatCurrency) { mutableSetOf<ChartRangeCacheKey>() }
    LaunchedEffect(selectedType, selectedRange, fiatCurrency, data, dataFiatCurrency, isLoading, failedToLoad) {
      val hasCurrentFiatData = data.isNotEmpty() && dataFiatCurrency == fiatCurrency
      if (isLoading || failedToLoad || !hasCurrentFiatData) return@LaunchedEffect

      val activeType = selectedType
      val activeFiatCurrency = fiatCurrency
      val activeCache = rangeCaches.forType(activeType)
      activeCache[selectedRange] = data

      ChartRange.entries
        .filter { range -> range != selectedRange && activeCache[range] == null }
        .forEach { range ->
          val cacheKey = ChartRangeCacheKey(activeType, range)
          if (!inFlightPrefetches.add(cacheKey)) return@forEach

          prefetchScope.launch {
            try {
              loadChartData(
                type = activeType,
                range = range
              )?.let { prefetchedData ->
                activeCache[range] = prefetchedData
                if (latestSelectedRequest == ChartSelectionKey(activeType, range, activeFiatCurrency)) {
                  onSelectedRangePrefetched(prefetchedData, activeFiatCurrency)
                }
              }
            } finally {
              inFlightPrefetches.remove(cacheKey)
            }
          }
        }
    }
  }

  @Composable
  private fun UpdateSelectedPointData(
    data: ImmutableList<DataPoint>,
    dataFiatCurrency: FiatCurrency?,
    selectedType: ChartType,
    selectedPoint: DataPoint?,
    isLoading: Boolean,
    latestExchangeRateValue: BigDecimal?,
    fiatCurrency: FiatCurrency,
    selectedRangeLabel: String,
    onSelectedPointDataChange: (SelectedPointData?) -> Unit,
  ) {
    LaunchedEffect(
      data,
      dataFiatCurrency,
      selectedType,
      selectedPoint,
      isLoading,
      latestExchangeRateValue,
      fiatCurrency,
      selectedRangeLabel
    ) {
      val hasStaleFiatData = data.isNotEmpty() && dataFiatCurrency != null && dataFiatCurrency != fiatCurrency
      if (isLoading || hasStaleFiatData) return@LaunchedEffect
      onSelectedPointDataChange(
        buildSelectedPointData(
          data = data,
          selectedType = selectedType,
          selectedPoint = selectedPoint,
          latestExchangeRateValue = latestExchangeRateValue,
          fiatCurrency = fiatCurrency,
          selectedRangeLabel = selectedRangeLabel
        )
      )
    }
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

  private suspend fun loadChartData(
    type: ChartType,
    range: ChartRange,
  ): ImmutableList<DataPoint>? {
    var prefetchedData: ImmutableList<DataPoint>? = null
    return when (type) {
      ChartType.BTC_PRICE -> {
        chartDataFetcherService.getChartData(range)
          .onSuccess { chartData -> prefetchedData = chartData.toImmutableDataPoints() }
        prefetchedData
      }
      ChartType.BALANCE -> {
        balanceHistoryService.observe(range)
          .first()
          .onSuccess { chartData -> prefetchedData = chartData.toImmutableDataPoints() }
        prefetchedData
      }
    }
  }

  private fun chartDataFlow(
    type: ChartType,
    range: ChartRange,
    latestExchangeRateFlow: Flow<*>,
  ) = when (type) {
    ChartType.BTC_PRICE ->
      latestExchangeRateFlow
        .map { chartDataFetcherService.getChartData(range) }

    ChartType.BALANCE ->
      balanceHistoryService.observe(range)
  }

  private fun buildSelectedPointData(
    data: ImmutableList<DataPoint>,
    selectedType: ChartType,
    selectedPoint: DataPoint?,
    latestExchangeRateValue: BigDecimal?,
    fiatCurrency: FiatCurrency,
    selectedRangeLabel: String,
  ): SelectedPointData {
    val selectedYValue = selectedPoint?.y?.toBigDecimal()
    return when (selectedType) {
      ChartType.BTC_PRICE -> {
        val pointValue = selectedYValue ?: latestExchangeRateValue
        val priceDiff = calculateDiffDecimal(data.firstOrNull()?.y, pointValue?.doubleValue(false))
        val primaryMoney = pointValue?.let { value -> FiatMoney(fiatCurrency, value) }
        SelectedPointData.BtcPrice(
          isUserSelected = selectedPoint != null,
          primaryText = primaryMoney?.let(moneyDisplayFormatter::format).orEmpty(),
          primaryValue = primaryMoney?.toAnimatedAmountValue(),
          secondaryText = formatSelectedDiffText(
            data.firstOrNull()?.y,
            pointValue?.doubleValue(false),
            includePrefix = false
          ).orEmpty(),
          secondaryTimePeriodText = selectedRangeLabel,
          direction = priceDiff?.let(PriceDirection::from) ?: PriceDirection.STABLE
        )
      }

      ChartType.BALANCE -> {
        val selectedBalanceAt = selectedPoint as? BalanceAt
        val startPoint = data.firstOrNull { it.y > 0.0 } as? BalanceAt
        val endPoint = (selectedPoint ?: data.lastOrNull()) as? BalanceAt
        val lastPoint = data.lastOrNull() as? BalanceAt
        val primaryFiatMoney = (selectedBalanceAt ?: lastPoint)?.run {
          FiatMoney(fiatCurrency, fiatBalance.toBigDecimal())
        }
        val primaryBtcMoney = endPoint?.run { BitcoinMoney.btc(balance) }
        SelectedPointData.Balance(
          isUserSelected = selectedPoint != null,
          primaryFiatText = primaryFiatMoney?.let(moneyDisplayFormatter::format).orEmpty(),
          primaryFiatValue = primaryFiatMoney?.toAnimatedAmountValue(),
          secondaryFiatText = formatSelectedDiffText(
            startPoint?.fiatBalance,
            lastPoint?.fiatBalance
          )?.run { "$this $selectedRangeLabel" }
            .orEmpty(),
          primaryBtcText = primaryBtcMoney?.let(moneyDisplayFormatter::format).orEmpty(),
          primaryBtcValue = primaryBtcMoney?.toAnimatedAmountValue(),
          secondaryBtcText = formatSelectedDiffText(
            startPoint?.balance,
            lastPoint?.balance
          )?.run { "$this $selectedRangeLabel" }
            .orEmpty()
        )
      }
    }
  }

  private fun handleChartTypeSelected(
    nextType: ChartType,
    currentType: ChartType,
    selectedRange: ChartRange,
    fiatCurrency: FiatCurrency,
    currentData: ImmutableList<DataPoint>,
    currentDataFiatCurrency: FiatCurrency?,
    rangeCaches: ChartRangeCaches,
    onDataChange: (ImmutableList<DataPoint>) -> Unit,
    onDataFiatCurrencyChange: (FiatCurrency?) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    onPreservePreviousChartWhileLoadingChange: (Boolean) -> Unit,
    onFailedToLoadChange: (Boolean) -> Unit,
    onSelectedPointChange: (DataPoint?) -> Unit,
    onTypeChange: (ChartType) -> Unit,
  ) {
    if (currentType == nextType) return

    val cachedData = rangeCaches.cachedData(nextType, selectedRange)
    onDataChange(cachedData ?: currentData)
    onDataFiatCurrencyChange(
      if (cachedData != null) fiatCurrency else currentDataFiatCurrency
    )
    onLoadingChange(cachedData == null)
    onPreservePreviousChartWhileLoadingChange(false)
    onFailedToLoadChange(false)
    onSelectedPointChange(null)
    onTypeChange(nextType)
  }

  private fun handleChartRangeSelected(
    nextRange: ChartRange,
    currentRange: ChartRange,
    selectedType: ChartType,
    fiatCurrency: FiatCurrency,
    currentData: ImmutableList<DataPoint>,
    currentDataFiatCurrency: FiatCurrency?,
    rangeCaches: ChartRangeCaches,
    onDataChange: (ImmutableList<DataPoint>) -> Unit,
    onDataFiatCurrencyChange: (FiatCurrency?) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    onPreservePreviousChartWhileLoadingChange: (Boolean) -> Unit,
    onFailedToLoadChange: (Boolean) -> Unit,
    onSelectedPointChange: (DataPoint?) -> Unit,
    onRangeChange: (ChartRange) -> Unit,
  ) {
    if (currentRange == nextRange) return

    val cachedData = rangeCaches.cachedData(selectedType, nextRange)
    onDataChange(cachedData ?: currentData)
    onDataFiatCurrencyChange(
      if (cachedData != null) fiatCurrency else currentDataFiatCurrency
    )
    onLoadingChange(cachedData == null)
    onPreservePreviousChartWhileLoadingChange(cachedData == null && currentData.isNotEmpty())
    onFailedToLoadChange(false)
    onSelectedPointChange(null)
    onRangeChange(nextRange)
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
   * Track selection start/stop separately from scrub-point crossings.
   */
  @Composable
  private fun GenerateSelectionLifecycleHapticFeedback(selectedPoint: DataPoint?) {
    var previouslySelectedPoint by remember { mutableStateOf<DataPoint?>(null) }
    LaunchedEffect(selectedPoint) {
      val selectionStartedOrStopped =
        (selectedPoint == null && previouslySelectedPoint != null) ||
          (selectedPoint != null && previouslySelectedPoint == null)
      if (selectionStartedOrStopped) {
        launch { haptics.vibrate(HapticsEffect.MediumClick) }
      }

      previouslySelectedPoint = selectedPoint
    }
  }

  private fun formatSelectedDiffText(
    start: Double?,
    end: Double?,
    includePrefix: Boolean = true,
  ): String? {
    val diffDecimal = calculateDiffDecimal(start, end) ?: return null
    val prefix = when {
      !includePrefix -> ""
      diffDecimal.isZero() -> ""
      else -> if (diffDecimal.isPositive) "+" else "-"
    }
    return "$prefix${diffDecimal.abs().toPlainString()}%"
  }

  private fun calculateDiffDecimal(
    start: Double?,
    end: Double?,
  ): BigDecimal? {
    return when {
      end == null -> null
      start == null || start == 0.0 -> end
      else -> (end - start) / start * 100
    }?.let { BigDecimal.fromDouble(it, DecimalMode.US_CURRENCY) }
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

  private fun List<out DataPoint>.toImmutableDataPoints(): ImmutableList<DataPoint> {
    return map { dataPoint -> dataPoint as DataPoint }.toImmutableList()
  }

  private data class ChartRangeCaches(
    val btc: MutableMap<ChartRange, ImmutableList<DataPoint>>,
    val balance: MutableMap<ChartRange, ImmutableList<DataPoint>>,
  ) {
    fun forType(type: ChartType): MutableMap<ChartRange, ImmutableList<DataPoint>> {
      return when (type) {
        ChartType.BTC_PRICE -> btc
        ChartType.BALANCE -> balance
      }
    }

    fun cachedData(
      type: ChartType,
      range: ChartRange,
    ): ImmutableList<DataPoint>? {
      return forType(type)[range]
    }
  }

  private data class ChartRangeCacheKey(
    val type: ChartType,
    val range: ChartRange,
  )

  private data class ChartSelectionKey(
    val type: ChartType,
    val range: ChartRange,
    val fiatCurrency: FiatCurrency,
  )
}
