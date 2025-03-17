package build.wallet.bitcoin.transactions

import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.Companion.sweepPriority
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes

class EstimatedTransactionPriorityTests : FunSpec({
  test("toDuration") {
    FASTEST.toDuration().shouldBe(10.minutes)
    THIRTY_MINUTES.toDuration().shouldBe(30.minutes)
    SIXTY_MINUTES.toDuration().shouldBe(60.minutes)
  }

  test("targetBlocks") {
    FASTEST.targetBlocks().shouldBe(1UL)
    THIRTY_MINUTES.targetBlocks().shouldBe(3UL)
    SIXTY_MINUTES.targetBlocks().shouldBe(6UL)
  }

  test("toFormattedString") {
    FASTEST.toFormattedString().shouldBe("~10 minutes")
    THIRTY_MINUTES.toFormattedString().shouldBe("~30 minutes")
    SIXTY_MINUTES.toFormattedString().shouldBe("~60 minutes")
  }

  test("Comparable") {
    FASTEST.shouldBeLessThan(THIRTY_MINUTES)
    FASTEST.shouldBeLessThan(SIXTY_MINUTES)
    THIRTY_MINUTES.shouldBeLessThan(SIXTY_MINUTES)
  }

  test("sweepPriority") {
    sweepPriority().shouldBe(THIRTY_MINUTES)
  }
})
