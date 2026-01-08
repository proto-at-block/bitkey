package build.wallet.bitcoin.fees

import build.wallet.money.BitcoinMoney

/**
 * Represents fee information from a **constructed** transaction.
 *
 * @property amount Calculated by taking the sum of non-change outputs minus sum of change output. Fees are an implicit calculation in bitcoin; and not actually specified in the bitcoin tx.
 */
data class Fee(
  val amount: BitcoinMoney,
)
