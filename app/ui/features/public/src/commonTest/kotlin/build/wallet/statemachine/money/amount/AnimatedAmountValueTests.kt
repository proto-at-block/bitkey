package build.wallet.statemachine.money.amount

import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AnimatedAmountValueTests : FunSpec({
  test("animation value uses the rounded display amount for fiat values") {
    FiatMoney.usd(1.994.toBigDecimal()).toAnimatedAmountValue().shouldBe(199L)
    FiatMoney.usd(1.995.toBigDecimal()).toAnimatedAmountValue().shouldBe(200L)
  }

  test("animation value rounds away from zero for negative fiat values") {
    FiatMoney.usd((-1.995).toBigDecimal()).toAnimatedAmountValue().shouldBe(-200L)
  }

  test("animation key changes when the displayed currency changes") {
    FiatMoney.usd(1.0).toAnimatedAmountAnimationKey().shouldBe("USD".hashCode().toLong())
    build.wallet.money.BitcoinMoney.btc(1.0).toAnimatedAmountAnimationKey()
      .shouldBe(BTC.textCode.code.hashCode().toLong())
  }
})
