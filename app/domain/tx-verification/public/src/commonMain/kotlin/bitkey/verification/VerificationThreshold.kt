package bitkey.verification

import build.wallet.money.BitcoinMoney
import build.wallet.money.Money

data class VerificationThreshold(val amount: Money) {
  init {
    require(!amount.isNegative) { "Cannot have a negative threshold" }
  }

  companion object {
    /**
     * Verification is enforced for any amount.
     */
    val Always = VerificationThreshold(BitcoinMoney.zero())
  }
}
