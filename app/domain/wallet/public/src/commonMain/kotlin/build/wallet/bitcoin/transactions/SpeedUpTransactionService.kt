package build.wallet.bitcoin.transactions

import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitkey.account.Account
import com.github.michaelbull.result.Result

/**
 * Service for speeding up pending Bitcoin transactions using Replace-By-Fee (RBF).
 *
 * Validates BIP125 requirements and constructs a replacement transaction with a higher fee rate.
 */
interface SpeedUpTransactionService {
  /**
   * Prepares a speed-up (replacement) transaction for an existing pending transaction.
   *
   * This validates that the transaction is eligible for RBF, constructs a new PSBT with an
   * increased fee rate, and returns the details needed to confirm and broadcast the replacement.
   *
   * @param account The account that owns the transaction.
   * @param transaction The pending transaction to speed up.
   * @return A [SpeedUpTransactionResult] containing the new PSBT, fee rate, and transaction
   *   details on success, or a [SpeedUpTransactionError] on failure.
   */
  suspend fun prepareTransactionSpeedUp(
    account: Account,
    transaction: BitcoinTransaction,
  ): Result<SpeedUpTransactionResult, SpeedUpTransactionError>
}

/**
 * Errors that can occur when preparing a transaction speed-up.
 */
sealed interface SpeedUpTransactionError {
  /**
   * Returned when required transaction data could not be prepared or validated.
   *
   * This can occur if recipient address or fee rate information is missing or invalid.
   */
  data object FailedToPrepareData : SpeedUpTransactionError

  /**
   * Returned when the wallet does not have sufficient funds to cover the increased fee.
   */
  data object InsufficientFunds : SpeedUpTransactionError

  /**
   * Returned when the new fee rate is not higher than the original transaction's fee rate.
   *
   * BIP125 requires replacement transactions to have a higher absolute fee.
   */
  data object FeeRateTooLow : SpeedUpTransactionError

  /**
   * Returned when the original transaction does not explicitly signal opt-in RBF as required by
   * BIP125 rule #1.
   *
   * Transactions must have at least one input with nSequence < 0xfffffffe to be replaceable.
   */
  data object TransactionNotReplaceable : SpeedUpTransactionError
}

/**
 * Result of preparing a transaction speed-up.
 *
 * @property psbt The new partially signed bitcoin transaction for the replacement.
 * @property newFeeRate The fee rate for the replacement transaction (higher than original).
 * @property details Transaction details including recipient and amounts for display/confirmation.
 */
data class SpeedUpTransactionResult(
  val psbt: Psbt,
  val newFeeRate: FeeRate,
  val details: SpeedUpTransactionDetails,
)
