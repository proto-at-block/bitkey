package build.wallet.balance.utils

import build.wallet.platform.config.AppVariant
import build.wallet.store.KeyValueStoreFactoryFake
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.Clock

class MockScenarioServiceImplTest : FunSpec({

  fun createService(
    appVariant: AppVariant = AppVariant.Development,
    keyValueStoreFactory: KeyValueStoreFactoryFake = KeyValueStoreFactoryFake(),
  ): MockScenarioServiceImpl {
    return MockScenarioServiceImpl(
      appVariant = appVariant,
      keyValueStoreFactory = keyValueStoreFactory,
      clock = Clock.System,
      mockTransactionDataGenerator = MockTransactionDataGenerator(Clock.System),
      mockPriceDataGenerator = MockPriceDataGenerator(Clock.System),
      coroutineScope = TestScope()
    )
  }

  test("should not allow setting mock scenarios in Customer builds") {
    val service = createService(AppVariant.Customer)

    shouldThrow<IllegalStateException> {
      service.setPriceScenario(MockPriceScenario.BULL_MARKET)
    }.message shouldBe "Mock scenarios are not available in Customer builds."

    shouldThrow<IllegalStateException> {
      service.setTransactionScenario(MockTransactionScenario.CASUAL_USER)
    }.message shouldBe "Mock scenarios are not available in Customer builds."
  }

  test("should return null for mock configuration in Customer builds") {
    val service = createService(AppVariant.Customer)

    service.currentMockConfiguration() shouldBe null
    service.currentPriceScenario() shouldBe null
    service.currentTransactionScenario() shouldBe null
  }

  test("should return null when no scenario is selected in Development builds") {
    val service = createService()

    service.currentMockConfiguration() shouldBe null
    service.currentPriceScenario() shouldBe null
    service.currentTransactionScenario() shouldBe null
    service.currentSeed() shouldBe null
  }

  test("should persist seed-based mock scenario selections independently") {
    val keyValueStoreFactory = KeyValueStoreFactoryFake()
    val service = createService(keyValueStoreFactory = keyValueStoreFactory)

    // Initially no scenario selected
    service.currentMockConfiguration() shouldBe null

    // Select scenarios together
    service.setPriceScenario(MockPriceScenario.BULL_MARKET)
    service.setTransactionScenario(MockTransactionScenario.CASUAL_USER)
    service.setDataQuality(DataQuality.Perfect)
    val config = service.currentMockConfiguration()
    config shouldNotBe null
    config?.priceScenario shouldBe MockPriceScenario.BULL_MARKET
    config?.transactionScenario shouldBe MockTransactionScenario.CASUAL_USER
    config?.dataQuality shouldBe DataQuality.Perfect
    config?.seed shouldNotBe null

    // Test individual scenario setting - should preserve other scenarios
    service.setPriceScenario(MockPriceScenario.VOLATILE_MARKET)
    service.currentPriceScenario() shouldBe MockPriceScenario.VOLATILE_MARKET
    service.currentTransactionScenario() shouldBe MockTransactionScenario.CASUAL_USER

    service.setTransactionScenario(MockTransactionScenario.WHALE)
    service.currentPriceScenario() shouldBe MockPriceScenario.VOLATILE_MARKET
    service.currentTransactionScenario() shouldBe MockTransactionScenario.WHALE

    // Test independent clearing
    service.clearScenarios(clearPrice = true, clearTransaction = false)
    service.currentSeed() shouldNotBe null
    service.currentPriceScenario() shouldBe null
    service.currentTransactionScenario() shouldBe MockTransactionScenario.WHALE

    service.clearScenarios(clearPrice = false, clearTransaction = true)
    service.currentSeed() shouldNotBe null
    service.currentPriceScenario() shouldBe null
    service.currentTransactionScenario() shouldBe null

    // Clear all
    service.setPriceScenario(MockPriceScenario.BULL_MARKET)
    service.setTransactionScenario(MockTransactionScenario.CASUAL_USER)
    service.setDataQuality(DataQuality.Perfect)
    service.clearScenarios()

    service.currentMockConfiguration() shouldBe null
    service.currentPriceScenario() shouldBe null
    service.currentTransactionScenario() shouldBe null
    service.currentSeed() shouldBe null

    service.currentTransactionScenarioFlow().first() shouldBe null
    service.currentPriceScenarioFlow().first() shouldBe null
    service.currentSeedFlow().first() shouldBe null
  }

  test("should work with independent scenarios") {
    val service = createService()

    // Set only price scenario - should create configuration with default transaction scenario
    service.setPriceScenario(MockPriceScenario.BULL_MARKET)
    service.currentPriceScenario() shouldBe MockPriceScenario.BULL_MARKET
    service.currentTransactionScenario() shouldBe null
    service.currentSeed() shouldNotBe null

    // Should return configuration with default transaction scenario
    val configWithPriceOnly = service.currentMockConfiguration()
    configWithPriceOnly shouldNotBe null
    configWithPriceOnly?.priceScenario shouldBe MockPriceScenario.BULL_MARKET
    configWithPriceOnly?.transactionScenario shouldBe null

    // Clear and set only transaction scenario - should create configuration with default price scenario
    service.clearScenarios()
    service.setTransactionScenario(MockTransactionScenario.WHALE)
    service.currentPriceScenario() shouldBe null // Should remain null
    service.currentTransactionScenario() shouldBe MockTransactionScenario.WHALE
    service.currentSeed() shouldNotBe null // Should have generated a seed

    // Should return configuration with default price scenario
    val configWithTransactionOnly = service.currentMockConfiguration()
    configWithTransactionOnly shouldNotBe null
    configWithTransactionOnly?.priceScenario shouldBe null
    configWithTransactionOnly?.transactionScenario shouldBe MockTransactionScenario.WHALE
  }

  test("should generate different seeds for repeated scenario setting") {
    val service = createService()

    // Set scenarios first time
    service.setPriceScenario(MockPriceScenario.BULL_MARKET)
    service.setTransactionScenario(MockTransactionScenario.CASUAL_USER)
    service.setDataQuality(DataQuality.Perfect)
    val config1 = service.currentMockConfiguration()

    // Clear and set same scenarios again - should get different seed
    service.clearScenarios()
    service.setPriceScenario(MockPriceScenario.BULL_MARKET)
    service.setTransactionScenario(MockTransactionScenario.CASUAL_USER)
    service.setDataQuality(DataQuality.Perfect)
    val config2 = service.currentMockConfiguration()

    // Same scenarios but different seeds (and timestamps)
    config1?.priceScenario shouldBe config2?.priceScenario
    config1?.transactionScenario shouldBe config2?.transactionScenario
    config1?.seed shouldNotBe config2?.seed
  }

  test("should allow setting configuration with custom seed") {
    val service = createService()

    val customSeed = 12345L
    val customConfig = MockConfiguration(
      priceScenario = MockPriceScenario.BULL_MARKET,
      transactionScenario = MockTransactionScenario.WHALE,
      dataQuality = DataQuality.Perfect,
      seed = customSeed,
      generatedAt = Clock.System.now()
    )

    // Set custom configuration
    service.setConfiguration(customConfig)

    val config = service.currentMockConfiguration()
    config shouldNotBe null
    config?.priceScenario shouldBe MockPriceScenario.BULL_MARKET
    config?.transactionScenario shouldBe MockTransactionScenario.WHALE
    config?.dataQuality shouldBe DataQuality.Perfect
    config?.seed shouldBe customSeed
  }

  test("should allow rotating seed without scenarios set") {
    val service = createService()

    // Initially no scenarios or seed
    service.currentSeed() shouldBe null
    service.currentDataQuality() shouldBe null

    // Rotate seed should work even without scenarios
    service.rotateSeed()

    // Should now have seed and default data quality
    service.currentSeed() shouldNotBe null
    service.currentDataQuality() shouldBe DataQuality.Perfect

    // But no configuration since no scenarios are set
    service.currentMockConfiguration() shouldBe null

    val firstSeed = service.currentSeed()

    // Rotate again should generate new seed
    service.rotateSeed()
    service.currentSeed() shouldNotBe firstSeed
  }
})
