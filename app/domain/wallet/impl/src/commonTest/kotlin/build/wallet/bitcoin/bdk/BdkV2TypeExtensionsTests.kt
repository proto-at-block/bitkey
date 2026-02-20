package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.money.BitcoinMoney
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BdkV2TypeExtensionsTests : FunSpec({

  context("FeeRate.toBdkV2FeeRate") {
    test("converts integer fee rate") {
      val feeRate = FeeRate(5.0f)
      val bdkFeeRate = feeRate.toBdkV2FeeRate()
      bdkFeeRate.toSatPerVbCeil().shouldBe(5UL)
    }

    test("rounds up fractional fee rate") {
      val feeRate = FeeRate(5.1f)
      val bdkFeeRate = feeRate.toBdkV2FeeRate()
      bdkFeeRate.toSatPerVbCeil().shouldBe(6UL)
    }

    test("rounds up small fractional fee rate") {
      val feeRate = FeeRate(1.01f)
      val bdkFeeRate = feeRate.toBdkV2FeeRate()
      bdkFeeRate.toSatPerVbCeil().shouldBe(2UL)
    }

    test("handles minimum fee rate of 1 sat/vB") {
      val feeRate = FeeRate(1.0f)
      val bdkFeeRate = feeRate.toBdkV2FeeRate()
      bdkFeeRate.toSatPerVbCeil().shouldBe(1UL)
    }

    test("preserves sat/kwu precision for fractional fee rate") {
      val feeRate = FeeRate(3.004f)
      val bdkFeeRate = feeRate.toBdkV2FeeRate()
      bdkFeeRate.toSatPerKwu().shouldBe(751UL)
    }
  }

  context("BitcoinMoney.toBdkV2Amount") {
    test("converts sats to Amount") {
      val money = BitcoinMoney.sats(1000)
      val amount = money.toBdkV2Amount()
      amount.toSat().shouldBe(1000UL)
    }

    test("converts zero sats") {
      val money = BitcoinMoney.sats(0)
      val amount = money.toBdkV2Amount()
      amount.toSat().shouldBe(0UL)
    }

    test("converts large amount") {
      val money = BitcoinMoney.sats(21_000_000_00_000_000L) // 21M BTC in sats
      val amount = money.toBdkV2Amount()
      amount.toSat().shouldBe(21_000_000_00_000_000UL)
    }
  }

  context("BdkOutPoint.toOutPoint") {
    test("converts outpoint with txid and vout") {
      val bdkOutPoint = BdkOutPoint(
        txid = "0000000000000000000000000000000000000000000000000000000000000001",
        vout = 0u
      )
      val outPoint = bdkOutPoint.toOutPoint()
      outPoint.txid.toString().shouldBe("0000000000000000000000000000000000000000000000000000000000000001")
      outPoint.vout.shouldBe(0u)
    }

    test("converts outpoint with non-zero vout") {
      val bdkOutPoint = BdkOutPoint(
        txid = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
        vout = 42u
      )
      val outPoint = bdkOutPoint.toOutPoint()
      outPoint.vout.shouldBe(42u)
    }
  }
})
