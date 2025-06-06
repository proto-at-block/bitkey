package bitkey.verification

import build.wallet.money.BitcoinMoney
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue

class VerificationThresholdTest : FunSpec({
  test("Compare Thresholds") {
    (VerificationThreshold.Disabled > VerificationThreshold.Always).shouldBeTrue()
    (VerificationThreshold.Disabled > VerificationThreshold.Enabled(BitcoinMoney.btc(Double.MAX_VALUE))).shouldBeTrue()
    (VerificationThreshold.Always < VerificationThreshold.Enabled(BitcoinMoney.btc(1.0))).shouldBeTrue()
    (VerificationThreshold.Enabled(BitcoinMoney.btc(1.0)) < VerificationThreshold.Enabled(BitcoinMoney.btc(2.0))).shouldBeTrue()
  }
})
