package build.wallet.balance.utils

import build.wallet.activity.Transaction
import build.wallet.activity.Transaction.BitcoinWalletTransaction
import build.wallet.bitcoin.BlockTime
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.platform.config.AppVariant
import build.wallet.store.KeyValueStoreFactory
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration

@BitkeyInject(AppScope::class)
@OptIn(ExperimentalSettingsApi::class)
class MockScenarioServiceImpl(
  private val appVariant: AppVariant,
  private val keyValueStoreFactory: KeyValueStoreFactory,
  private val clock: Clock,
  private val mockTransactionDataGenerator: MockTransactionDataGenerator,
  private val mockPriceDataGenerator: MockPriceDataGenerator,
  coroutineScope: CoroutineScope,
) : MockScenarioService {
  private companion object {
    const val PRICE_SCENARIO_KEY = "mock_price_scenario"
    const val TRANSACTION_SCENARIO_KEY = "mock_transaction_scenario"
    const val DATA_QUALITY_KEY = "mock_data_quality"
    const val SEED_KEY = "mock_seed"
    const val GENERATED_AT_KEY = "mock_generated_at"
    const val MOCK_SCENARIO_STORE_NAME = "MOCK_SCENARIO_STORE"
  }

  private suspend fun getStore(): SuspendSettings =
    keyValueStoreFactory.getOrCreate(MOCK_SCENARIO_STORE_NAME)

  private var currentPriceScenario: MockPriceScenario? = null
  private var currentTransactionScenario: MockTransactionScenario? = null
  private var currentDataQuality: DataQuality? = null
  private var currentSeed: Long? = null
  private var currentGeneratedAt: Instant? = null

  // StateFlows for reactive updates
  private val _currentPriceScenarioFlow = MutableStateFlow<MockPriceScenario?>(null)
  private val _currentTransactionScenarioFlow = MutableStateFlow<MockTransactionScenario?>(null)
  private val _currentSeedFlow = MutableStateFlow<Long?>(null)

  init {
    if (appVariant != AppVariant.Customer) {
      coroutineScope.launch {
        loadPersistedState()
      }
    }
  }

  private suspend fun loadPersistedState() {
    val store = getStore()

    currentPriceScenario = store.getStringOrNull(PRICE_SCENARIO_KEY)?.let { scenarioName ->
      MockPriceScenario.entries.find { it.name == scenarioName }
    }
    _currentPriceScenarioFlow.value = currentPriceScenario

    currentTransactionScenario = store.getStringOrNull(TRANSACTION_SCENARIO_KEY)?.let { scenarioName ->
      MockTransactionScenario.entries.find { it.name == scenarioName }
    }
    _currentTransactionScenarioFlow.value = currentTransactionScenario

    currentDataQuality = store.getStringOrNull(DATA_QUALITY_KEY)?.let { qualityName ->
      DataQuality.entries.find { it.name == qualityName }
    }

    currentSeed = store.getLongOrNull(SEED_KEY)
    _currentSeedFlow.value = currentSeed

    currentGeneratedAt = store.getStringOrNull(GENERATED_AT_KEY)?.let { timestamp ->
      runCatching { Instant.parse(timestamp) }.getOrElse {
        throw IllegalStateException("Failed to parse stored timestamp: $timestamp", it)
      }
    }
  }

  override suspend fun currentMockConfiguration(): MockConfiguration? {
    if (appVariant == AppVariant.Customer) return null

    // Only return configuration if at least one scenario is explicitly set
    if (currentPriceScenario == null && currentTransactionScenario == null) return null

    val dataQuality = currentDataQuality ?: return null
    val seed = currentSeed ?: return null
    val generatedAt = currentGeneratedAt ?: return null

    return MockConfiguration(
      priceScenario = currentPriceScenario,
      transactionScenario = currentTransactionScenario,
      dataQuality = dataQuality,
      seed = seed,
      generatedAt = generatedAt
    )
  }

  override suspend fun currentPriceScenario(): MockPriceScenario? = currentPriceScenario

  override suspend fun currentTransactionScenario(): MockTransactionScenario? =
    currentTransactionScenario

  override suspend fun currentDataQuality(): DataQuality? = currentDataQuality

  override suspend fun currentSeed(): Long? = currentSeed

  override fun currentTransactionScenarioFlow(): Flow<MockTransactionScenario?> =
    _currentTransactionScenarioFlow.asStateFlow()

  override fun currentPriceScenarioFlow(): Flow<MockPriceScenario?> =
    _currentPriceScenarioFlow.asStateFlow()

  override fun currentSeedFlow(): Flow<Long?> = _currentSeedFlow.asStateFlow()

  override suspend fun setPriceScenario(scenario: MockPriceScenario) {
    checkNotCustomerBuild()

    currentPriceScenario = scenario
    _currentPriceScenarioFlow.value = scenario
    ensureRequiredMetadata()
    persistState()
  }

  override suspend fun setTransactionScenario(scenario: MockTransactionScenario) {
    checkNotCustomerBuild()

    currentTransactionScenario = scenario
    _currentTransactionScenarioFlow.value = scenario
    ensureRequiredMetadata()
    persistState()
  }

  override suspend fun setDataQuality(dataQuality: DataQuality) {
    checkNotCustomerBuild()

    currentDataQuality = dataQuality
    ensureRequiredMetadata()
    persistState()
  }

  override suspend fun setConfiguration(
    config: MockConfiguration,
    generateNewSeed: Boolean,
  ) {
    checkNotCustomerBuild()

    currentPriceScenario = config.priceScenario
    currentTransactionScenario = config.transactionScenario
    currentDataQuality = config.dataQuality

    _currentPriceScenarioFlow.value = currentPriceScenario
    _currentTransactionScenarioFlow.value = currentTransactionScenario

    if (generateNewSeed) {
      currentSeed = Random.nextLong()
      currentGeneratedAt = clock.now()
    } else {
      currentSeed = config.seed
      currentGeneratedAt = config.generatedAt
    }

    _currentSeedFlow.value = currentSeed

    persistState()
  }

  /**
   * Convenience method to rotate the seed while keeping current scenarios.
   */
  override suspend fun rotateSeed() {
    checkNotCustomerBuild()

    // Generate new seed and timestamp
    currentSeed = Random.nextLong()
    currentGeneratedAt = clock.now()

    // Ensure we have default data quality if none is set
    if (currentDataQuality == null) {
      currentDataQuality = DataQuality.Perfect
    }

    persistState()

    // Update the seed flow to trigger reactive updates
    _currentSeedFlow.value = currentSeed
  }

  override suspend fun clearScenarios(
    clearPrice: Boolean,
    clearTransaction: Boolean,
  ) {
    if (appVariant == AppVariant.Customer) return

    if (clearPrice && clearTransaction) {
      currentPriceScenario = null
      currentTransactionScenario = null
      currentDataQuality = null
      currentSeed = null
      currentGeneratedAt = null

      _currentPriceScenarioFlow.value = null
      _currentTransactionScenarioFlow.value = null
      _currentSeedFlow.value = null
    } else if (clearPrice) {
      currentPriceScenario = null
      _currentPriceScenarioFlow.value = null
    } else if (clearTransaction) {
      currentTransactionScenario = null
      _currentTransactionScenarioFlow.value = null
    }

    persistState()
  }

  override suspend fun generateTransactions(): List<Transaction> {
    if (appVariant == AppVariant.Customer) return emptyList()
    val config = currentMockConfiguration() ?: return emptyList()
    val transactionScenario = config.transactionScenario ?: return emptyList()
    val mockTransactionData = mockTransactionDataGenerator.generateMockTransactionData(
      scenario = transactionScenario,
      seed = config.seed
    )
    return convertMockTransactionsToRealFormat(mockTransactionData)
  }

  override suspend fun generatePriceData(
    maxPoints: Int,
    fiatCurrency: FiatCurrency,
    timeRange: Duration,
  ): List<MockDataPoint> {
    if (appVariant == AppVariant.Customer) return emptyList()
    val config = currentMockConfiguration() ?: return emptyList()
    val priceScenario = config.priceScenario ?: return emptyList()
    return mockPriceDataGenerator.generateMockPriceData(
      scenario = priceScenario,
      dataQuality = config.dataQuality,
      seed = config.seed,
      maxPoints = maxPoints,
      fiatCurrency = fiatCurrency,
      timeRange = timeRange
    )
  }

  /**
   * Ensures that we have all the required metadata (data quality, seed, timestamp)
   * for generating mock data. Creates defaults if any are missing.
   */
  private fun ensureRequiredMetadata() {
    if (currentDataQuality == null) {
      currentDataQuality = DataQuality.Perfect
    }

    if (currentSeed == null || currentGeneratedAt == null) {
      currentSeed = Random.nextLong()
      currentGeneratedAt = clock.now()
      _currentSeedFlow.value = currentSeed
    }
  }

  /**
   * Persists the current state to the key-value store.
   */
  private suspend fun persistState() {
    val store = getStore()

    currentPriceScenario?.let {
      store.putString(PRICE_SCENARIO_KEY, it.name)
    } ?: store.remove(PRICE_SCENARIO_KEY)

    currentTransactionScenario?.let {
      store.putString(TRANSACTION_SCENARIO_KEY, it.name)
    } ?: store.remove(TRANSACTION_SCENARIO_KEY)

    currentDataQuality?.let {
      store.putString(DATA_QUALITY_KEY, it.name)
    } ?: store.remove(DATA_QUALITY_KEY)

    currentSeed?.let {
      store.putLong(SEED_KEY, it)
    } ?: store.remove(SEED_KEY)

    currentGeneratedAt?.let {
      store.putString(GENERATED_AT_KEY, it.toString())
    } ?: store.remove(GENERATED_AT_KEY)
  }

  private fun checkNotCustomerBuild() {
    check(appVariant != AppVariant.Customer) {
      "Mock scenarios are not available in Customer builds."
    }
  }

  /**
   * Convert simplified mock transaction data to BitcoinTransaction objects wrapped in BitcoinWalletTransaction.
   * Only includes fields needed for transaction list display.
   */
  private fun convertMockTransactionsToRealFormat(
    mockTransactions: List<MockTransactionData>,
  ): List<Transaction> {
    return mockTransactions.map { mockTx ->
      val amount = BitcoinMoney.sats(abs(mockTx.amountSats))
      val fee = if (mockTx.feeSats > 0) BitcoinMoney.sats(mockTx.feeSats) else null

      val bitcoinTransaction = BitcoinTransaction(
        id = mockTx.id,
        recipientAddress = null,
        broadcastTime = mockTx.timestamp.minus(Duration.parse("5m")),
        confirmationStatus = ConfirmationStatus.Confirmed(
          blockTime = BlockTime(
            height = generateBlockHeight(mockTx.timestamp).toLong(),
            timestamp = mockTx.timestamp
          )
        ),
        vsize = null,
        weight = null,
        fee = fee,
        subtotal = amount,
        total = if (mockTx.isIncoming) amount else amount + (fee ?: BitcoinMoney.zero()),
        transactionType = if (mockTx.isIncoming) TransactionType.Incoming else TransactionType.Outgoing,
        estimatedConfirmationTime = null,
        inputs = persistentListOf(),
        outputs = persistentListOf()
      )

      BitcoinWalletTransaction(details = bitcoinTransaction)
    }.sortedByDescending { it.details.confirmationTime() }
  }

  private fun generateBlockHeight(timestamp: kotlinx.datetime.Instant): UInt {
    // Approximate block height based on timestamp (roughly 10 minutes per block)
    val blocksSinceGenesis = (timestamp.toEpochMilliseconds() - 1231006505000L) / (10 * 60 * 1000)
    return if (blocksSinceGenesis.toUInt() > 1u) blocksSinceGenesis.toUInt() else 1u
  }
}
