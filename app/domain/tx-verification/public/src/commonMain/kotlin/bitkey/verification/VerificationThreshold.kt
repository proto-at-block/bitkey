package bitkey.verification

import build.wallet.money.BitcoinMoney
import build.wallet.money.Money
import kotlin.jvm.JvmInline

/**
 * Send Limit for requiring Transaction Verification.
 */
sealed interface VerificationThreshold {
  /**
   * Optional limit that requires transaction verification.
   */
  val amount: Money?

  /**
   * Verification should be used when the transaction meets the limit specified.
   */
  @JvmInline
  value class Enabled(override val amount: Money) : VerificationThreshold {
    init {
      require(!amount.isNegative) { "Cannot have a negative threshold" }
    }
  }

  /**
   * Verification is disabled and not needed for any transactions.
   */
  data object Disabled : VerificationThreshold {
    override val amount: Money? = null
  }

  companion object {
    /**
     * Verification is enforced for any amount.
     */
    val Always = Enabled(BitcoinMoney.zero())
  }
}
