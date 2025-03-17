package build.wallet.configuration

import build.wallet.configuration.MobilePayFiatConfig.SnapTolerance
import build.wallet.money.FiatMoney
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class MobilePayFiatConfigTests : FunSpec({
  context("consistency checks") {
    test("limits should use the same currency") {
      shouldThrow<IllegalArgumentException> {
        MobilePayFiatConfig(
          minimumLimit = FiatMoney.usd(0),
          maximumLimit = FiatMoney.eur(10),
          snapValues = emptyMap()
        )
      }
    }

    test("snap values should use the same currency as limits") {
      shouldThrow<IllegalArgumentException> {
        MobilePayFiatConfig(
          minimumLimit = FiatMoney.usd(0),
          maximumLimit = FiatMoney.usd(10),
          snapValues = mapOf(
            FiatMoney.eur(5) to SnapTolerance(FiatMoney.usd(1))
          )
        )
      }
    }

    test("snap tolerances should use the same currency as limits") {
      shouldThrow<IllegalArgumentException> {
        MobilePayFiatConfig(
          minimumLimit = FiatMoney.usd(0),
          maximumLimit = FiatMoney.usd(10),
          snapValues = mapOf(
            FiatMoney.usd(5) to SnapTolerance(FiatMoney.eur(1))
          )
        )
      }
    }

    test("maximum limit should be higher than minimum limit") {
      shouldThrow<IllegalArgumentException> {
        MobilePayFiatConfig(
          minimumLimit = FiatMoney.usd(10),
          maximumLimit = FiatMoney.usd(5),
          snapValues = emptyMap()
        )
      }
    }
  }
})
