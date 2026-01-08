package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bitcoin.BlockTime
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.logging.logError
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.BTC
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class BitcoinTransaction(
  /**
   * The txid of the transaction.
   */
  val id: String,
  /**
   * The address the transaction was sent to.
   */
  val recipientAddress: BitcoinAddress?,
  /**
   * The time the transaction was broadcast to the blockchain.
   */
  val broadcastTime: Instant?,
  /**
   * The confirmation status (either [Pending] or [Confirmed])
   * of the transaction.
   */
  val confirmationStatus: ConfirmationStatus,
  /**
   * Virtual size of transaction, in vBytes (vB).
   */
  val vsize: ULong?,
  /**
   * Weight of transaction, in weight units (WU).
   */
  val weight: ULong?,
  /**
   * The fee amount of the transaction, specified in BTC.
   */
  val fee: BitcoinMoney?,
  /**
   * The total amount of the transaction, excluding fees, specified in BTC.
   */
  val subtotal: BitcoinMoney,
  /**
   * The total amount of the transaction, including fees, specified in BTC.
   */
  val total: BitcoinMoney,
  /**
   * Whether the transaction was incoming (a receive transaction)
   * or outgoing (a send transaction)
   */
  val transactionType: TransactionType,
  /**
   * The estimatedConfirmationTime represents the anticipated time for transaction confirmation,
   * dependent on the selected user's fee rate.
   *
   * If this value dates back to the past, the transaction fees might have been improperly estimated
   * at the time of the transaction broadcast. If the value points to the future, it indicates that
   * the transaction confirmation is expected to occur within the window we guaranteed to the
   * customer.
   */
  val estimatedConfirmationTime: Instant?,
  /**
   * The transaction's inputs.
   */
  val inputs: ImmutableList<BdkTxIn>,
  /**
   * The transaction's outputs.
   */
  val outputs: ImmutableList<BdkTxOut>,
) {
  init {
    require(!subtotal.isNegative)
    require(!total.isNegative)
    fee?.let { require(!it.isNegative) }
  }

  /**
   * The time the transaction was confirmed, if it is confirmed, otherwise null.
   */
  fun confirmationTime(): Instant? =
    when (confirmationStatus) {
      is Confirmed -> confirmationStatus.blockTime.timestamp
      is Pending -> null
    }

  /**
   * Only first 4 and last 4 characters are kept, with "..." left in between.
   * For example, for full ID "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b",
   * truncated ID is "4a5e...a33b".
   */
  fun truncatedId(): String =
    id.let {
      "${it.take(4)}...${it.takeLast(4)}"
    }

  fun truncatedRecipientAddress(): String {
    if (recipientAddress == null) {
      // This should be unexpected, so handle with an empty string and log if it ever occurs.
      logError { "Missing recipient address for transaction $id" }
      return ""
    }

    return recipientAddress.truncatedAddress()
  }

  fun chunkedRecipientAddress(): String {
    if (recipientAddress == null) {
      // This should be unexpected, so handle with an empty string and log if it ever occurs.
      logError { "Missing recipient address for transaction $id" }
      return ""
    }

    return recipientAddress.chunkedAddress()
  }

  /** Calculates the fee rate for the transaction. Returns null if fee is not defined. */
  fun feeRate(): FeeRate? {
    return if (vsize != null && fee != null) {
      val feeRateValue =
        BTC.fractionalUnitValueFromUnitValue(fee.value).divide(BigInteger(vsize.toLong()))
          .floatValue(exactRequired = false)
      FeeRate(satsPerVByte = feeRateValue)
    } else {
      null
    }
  }

  sealed interface ConfirmationStatus {
    data object Pending : ConfirmationStatus

    data class Confirmed(val blockTime: BlockTime) : ConfirmationStatus
  }

  /**
   * Indicates the type of transaction, e.g. incoming (a receive transaction)
   * or outgoing (a send transaction).
   */
  sealed interface TransactionType {
    data object Incoming : TransactionType

    data object Outgoing : TransactionType

    /**
     * The transaction is a UTXO Consolidation, meaning the user combined multiple UTXOs
     * into a single UTXO. Today, we only allow consolidation all non-pending UTXOs; the user
     * cannot select individual UTXOs to consolidate.
     */
    data object UtxoConsolidation : TransactionType
  }
}

fun BitcoinTransaction.toSpeedUpTransactionDetails(): SpeedUpTransactionDetails? {
  // This should be unexpected, so we handle with returning null and log and error if it occurs.
  if (recipientAddress == null || fee == null) {
    logError { "Missing recipient address or fee information for transaction $id" }
    return null
  }

  return SpeedUpTransactionDetails(
    txid = this.id,
    recipientAddress = recipientAddress,
    oldFee = Fee(amount = fee),
    sendAmount = this.subtotal,
    transactionType = this.transactionType
  )
}

fun BitcoinTransaction.isLate(clock: Clock): Boolean {
  return estimatedConfirmationTime?.let { estimatedTime ->
    clock.now() > estimatedTime
  } == true
}
