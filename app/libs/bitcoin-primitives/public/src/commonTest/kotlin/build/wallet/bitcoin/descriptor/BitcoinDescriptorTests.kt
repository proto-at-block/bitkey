package build.wallet.bitcoin.descriptor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BitcoinDescriptorTests : FunSpec({
  context("redact descriptors") {
    test("SpendingDescriptor") {
      BitcoinDescriptor.Spending(raw = "secret").toString().shouldBe("Spending(██)")
    }

    test("WatchingDescriptor") {
      BitcoinDescriptor.Watching(raw = "secret").toString().shouldBe("Watching(██)")
    }
  }
})
