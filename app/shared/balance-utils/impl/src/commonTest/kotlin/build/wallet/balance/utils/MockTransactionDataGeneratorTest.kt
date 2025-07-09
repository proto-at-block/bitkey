package build.wallet.balance.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Clock

class MockTransactionDataGeneratorTest : FunSpec({

  test("should generate empty list for EMPTY_WALLET scenario") {
    val generator = MockTransactionDataGenerator(Clock.System)

    val transactions = generator.generateMockTransactionData(
      MockTransactionScenario.EMPTY_WALLET,
      12345L
    )

    transactions shouldHaveSize 0
  }

  test("should generate deterministic transactions for same seed") {
    val fixedTime = kotlinx.datetime.Instant.parse("2024-06-05T19:41:43.104Z")
    val fixedClock = object : Clock {
      override fun now() = fixedTime
    }
    val generator = MockTransactionDataGenerator(fixedClock)
    val seed = 54321L

    val transactions1 = generator.generateMockTransactionData(
      MockTransactionScenario.CASUAL_USER,
      seed
    )

    val transactions2 = generator.generateMockTransactionData(
      MockTransactionScenario.CASUAL_USER,
      seed
    )

    transactions1 shouldBe transactions2
    transactions1.size shouldNotBe 0
  }

  test("should generate different transactions for different scenarios") {
    val generator = MockTransactionDataGenerator(Clock.System)
    val seed = 98765L

    val hodlerTransactions = generator.generateMockTransactionData(
      MockTransactionScenario.HODLER,
      seed
    )

    val dayTraderTransactions = generator.generateMockTransactionData(
      MockTransactionScenario.DAY_TRADER,
      seed
    )

    // HODLer should have fewer transactions than day trader
    hodlerTransactions.size shouldBeLessThan dayTraderTransactions.size
  }

  test("should maintain balance constraints") {
    val generator = MockTransactionDataGenerator(Clock.System)

    val transactions = generator.generateMockTransactionData(
      MockTransactionScenario.CASUAL_USER,
      11111L
    )

    // Verify balance never goes negative
    var balance = 0L
    transactions.forEach { tx ->
      if (tx.isIncoming) {
        balance += tx.amountSats
      } else {
        balance += tx.amountSats - tx.feeSats // amountSats is negative for outgoing
      }
      balance shouldBeGreaterThanOrEqual 0L
    }
  }

  test("should generate realistic transaction amounts") {
    val generator = MockTransactionDataGenerator(Clock.System)

    val transactions = generator.generateMockTransactionData(
      MockTransactionScenario.CASUAL_USER,
      22222L
    )

    // Casual user should have reasonable amounts
    transactions.forEach { tx ->
      val absAmount = kotlin.math.abs(tx.amountSats)
      absAmount shouldBeGreaterThanOrEqual 546L // Above dust limit
    }
  }
})
