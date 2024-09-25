package build.wallet.bitcoin.transactions

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BitcoinTransactionIdTests : FunSpec({
  test("transaction ID cannot be blank") {
    shouldThrow<IllegalArgumentException> {
      BitcoinTransactionId(value = "   ")
    }.message.shouldBe("Transaction ID cannot be blank")
  }
  test("truncate") {
    BitcoinTransactionId("0c7cf7e2d912832e032bd1e0ed1c29b73d05df4834589cde543ffb58752e1eb8")
      .truncated()
      .shouldBe("0c7cf7e2...752e1eb8")
  }
})
