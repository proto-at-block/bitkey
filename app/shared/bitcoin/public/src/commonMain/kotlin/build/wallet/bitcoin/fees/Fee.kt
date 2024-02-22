package build.wallet.bitcoin.fees

import build.wallet.money.BitcoinMoney

/**
 * Represents fee information from a **constructed** transaction.
 *
 * @property amount Calculated by taking the sum of non-change outputs minus sum of change output. Fees are an implicit calculation in bitcoin; and not actually specified in the bitcoin tx.
 * @property feeRate Calculated by taking [amount] and dividing it by vsize. In sats/vB.
 */
data class Fee(
  val amount: BitcoinMoney,
  val feeRate: FeeRate,
)
