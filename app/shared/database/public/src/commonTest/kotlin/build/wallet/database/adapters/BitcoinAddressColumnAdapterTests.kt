package build.wallet.database.adapters

import build.wallet.bitcoin.address.someBitcoinAddress
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BitcoinAddressColumnAdapterTests : FunSpec({

  val address = someBitcoinAddress

  test("decode BitcoinAddress from string") {
    BitcoinAddressColumnAdapter.decode(address.address).shouldBe(address)
  }

  test("encode BitcoinAddress as string") {
    BitcoinAddressColumnAdapter.encode(address).shouldBe(address.address)
  }
})
