package build.wallet.bitcoin.address

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BitcoinAddressTests : FunSpec({

  test("address chunking") {
    val address = BitcoinAddress("15e15hWo6CShMgbAfo8c2Ykj4C6BLq6Not")
    address.chunkedAddress().shouldBe("15e1 5hWo 6CSh MgbA fo8c 2Ykj 4C6B Lq6N ot")
  }

  test("address truncation") {
    val address = BitcoinAddress("15e15hWo6CShMgbAfo8c2Ykj4C6BLq6Not")
    address.truncatedAddress().shouldBe("15e1...6Not")
  }
})
