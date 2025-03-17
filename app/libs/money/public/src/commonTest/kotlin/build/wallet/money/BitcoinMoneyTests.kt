package build.wallet.money

import build.wallet.money.BitcoinMoney.Companion.btc
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BitcoinMoneyTests : FunSpec({
  test("orZero returns the same value when it is not null") {
    btc(1.0).orZero().shouldBe(btc(1.0))
  }

  test("orZero returns zero when the value is null") {
    val amount: BitcoinMoney? = null
    amount.orZero().shouldBe(btc(0.0))
  }
})
