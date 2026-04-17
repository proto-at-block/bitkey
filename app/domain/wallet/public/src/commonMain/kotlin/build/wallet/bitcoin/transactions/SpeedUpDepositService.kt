package build.wallet.bitcoin.transactions

import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Result

/**
 * Service for accelerating stuck incoming Bitcoin transactions using Child Pays for Parent (CPFP).
 *
 * CPFP creates a new child transaction that spends an unconfirmed incoming output back to the
 * wallet with a fee high enough that the effective package fee rate (parent + child) meets the
 * target priority. This incentivizes miners to confirm both transactions together.
 */
interface SpeedUpDepositService {
  /**
   * Prepares a CPFP child transaction for an incoming pending transaction.
   *
   * Identifies the wallet-owned unconfirmed output from the parent transaction, calculates
   * the required child fee to bring the package up to the fastest fee rate, and constructs
   * a signed PSBT ready for hardware confirmation.
   *
   * @param transaction The incoming pending transaction to accelerate.
   * @return A [SpeedUpDepositTransaction] on success, or an [Error] on failure.
   */
  suspend fun prepareSpeedUpDepositTransaction(
    transaction: BitcoinTransaction,
  ): Result<SpeedUpDepositTransaction, Error>
}

/**
 * Result of preparing a CPFP speed-up transaction.
 *
 * @property psbt The app-signed PSBT for the child transaction, ready for hardware signing.
 * @property childFeeRate The effective fee rate of the child transaction.
 * @property parentTxid The txid of the incoming (parent) transaction being accelerated.
 * @property parentFee The fee the parent transaction originally paid.
 * @property childFee The fee the child transaction will pay.
 * @property transferAmount The amount being transferred in the child (sent back to self).
 */
data class SpeedUpDepositTransaction(
  val psbt: Psbt,
  val childFeeRate: FeeRate,
  val parentTxid: String,
  val parentFee: Fee,
  val childFee: Fee,
  val transferAmount: BitcoinMoney,
)
