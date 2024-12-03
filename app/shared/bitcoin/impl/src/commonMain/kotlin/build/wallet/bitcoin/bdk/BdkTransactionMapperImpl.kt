package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.*
import build.wallet.bdk.bindings.BdkResult.Err
import build.wallet.bdk.bindings.BdkResult.Ok
import build.wallet.bitcoin.BlockTime
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailDao
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.feature.flags.UtxoConsolidationFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logError
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.get
import kotlinx.collections.immutable.toImmutableList

class BdkTransactionMapperImpl(
  private val bdkAddressBuilder: BdkAddressBuilder,
  private val outgoingTransactionDetailDao: OutgoingTransactionDetailDao,
  private val utxoConsolidationFeatureFlag: UtxoConsolidationFeatureFlag,
) : BdkTransactionMapper {
  override suspend fun createTransaction(
    bdkTransaction: BdkTransactionDetails,
    bdkNetwork: BdkNetwork,
    bdkWallet: BdkWallet,
  ): BitcoinTransaction {
    // Sum of owned inputs of this transaction.
    // AS A RECEIVER
    //    This value is always 0, since you will not own any of the inputs.
    // AS A SENDER
    //    This value will not be 0, since you will have to put up your UTXOs as inputs.
    // AS A UTXO CONSOLIDATION
    //    This value will not be 0, since you are "sending" to yourself.
    val sent = BitcoinMoney.sats(bdkTransaction.sent)

    // Sum of owned outputs of this transaction.
    // AS A RECEIVER
    //  The value will not be 0, since you will have some UTXOs that you now own.
    // AS A SENDER
    //  WHEN received == 0
    //    This is likely a sweep-out transaction.
    //  WHEN received != 0
    //    The value here represents the change.
    // AS A UTXO CONSOLIDATION
    //    This value will not be 0, since you are receiving a new UTXO that you now own.
    val received = BitcoinMoney.sats(bdkTransaction.received)

    // If sent amount is zero, that means this transaction is one where you only received.
    val isZeroSumTransaction = sent.isZero && !received.isZero

    val fee = bdkTransaction.fee?.let { BitcoinMoney.sats(it) }
    val transactionWeight = bdkTransaction.transaction?.weight()
    val vsize = bdkTransaction.transaction?.vsize()

    val isUtxoConsolidation = if (utxoConsolidationFeatureFlag.isEnabled()) {
      bdkTransaction.transaction?.isUtxoConsolidation(bdkWallet, sent) ?: false
    } else {
      false
    }

    val total =
      if (isZeroSumTransaction || isUtxoConsolidation) {
        received + (fee ?: BitcoinMoney.zero())
      } else {
        sent - received
      }

    // If this a receive, the subtotal is just how much you received.
    val subtotal =
      if (isZeroSumTransaction || isUtxoConsolidation) {
        received
      } else {
        total - (fee ?: BitcoinMoney.zero())
      }

    val transactionType = when {
      sent.isZero -> Incoming
      isUtxoConsolidation -> UtxoConsolidation
      else -> Outgoing
    }

    return BitcoinTransaction(
      id = bdkTransaction.txid,
      recipientAddress =
        bdkTransaction.transaction?.recipientAddress(
          bdkNetwork,
          bdkWallet,
          transactionType
        ),
      broadcastTime =
        outgoingTransactionDetailDao.broadcastTimeForTransaction(
          transactionId = bdkTransaction.txid
        ),
      estimatedConfirmationTime =
        outgoingTransactionDetailDao.confirmationTimeForTransaction(
          transactionId = bdkTransaction.txid
        ),
      confirmationStatus = bdkTransaction.confirmationStatus(),
      subtotal = subtotal,
      total = total,
      fee = fee,
      vsize = vsize,
      weight = transactionWeight,
      transactionType = transactionType,
      inputs = bdkTransaction.transaction?.input()?.toImmutableList() ?: emptyImmutableList(),
      outputs = bdkTransaction.transaction?.output()?.toImmutableList() ?: emptyImmutableList()
    )
  }

  /**
   * Produce our own [BitcoinTransaction.ConfirmationStatus] type from BDK's [BdkTransactionDetails].
   */
  private fun BdkTransactionDetails.confirmationStatus(): BitcoinTransaction.ConfirmationStatus {
    return when (val confirmationTime = confirmationTime) {
      null -> Pending
      else ->
        Confirmed(
          blockTime = confirmationTime.blockTime
        )
    }
  }

  /**
   * Extract the address the transaction was sent to and return, if possible.
   */
  private suspend fun BdkTransaction.recipientAddress(
    bdkNetwork: BdkNetwork,
    bdkWallet: BdkWallet,
    transactionType: TransactionType,
  ): BitcoinAddress? {
    // Find the TxOut that does or does not correspond to the current wallet based on [transactionType]
    val addressTxOut =
      output()
        .firstOrNull {
          when (val isMine = bdkWallet.isMine(it.scriptPubkey)) {
            is Ok -> when (transactionType) {
              Incoming, UtxoConsolidation -> isMine.value
              Outgoing -> !isMine.value
            }
            is Err -> {
              // Early return null for [recipientAddress] if we were unable to determine [isMine]
              logError(throwable = isMine.error) { "Error calling isMine for wallet script" }
              return null
            }
          }
        } ?: return null // Early return null if [addressTxOut] is null

    // Use [BdkAddressBuilder] to convert the script of that TxOut to an address we can display
    val bdkAddress =
      bdkAddressBuilder.build(
        script = addressTxOut.scriptPubkey,
        network = bdkNetwork
      )
    return when (bdkAddress) {
      is Ok -> BitcoinAddress(bdkAddress.value.asString())
      is Err -> {
        logError(throwable = bdkAddress.error) { "Error building bdk address" }
        null
      }
    }
  }
}

/**
 * Indicates whether a transaction is considered a UTXO consolidation; that is, whether all outputs
 * have a destination of [myWallet].
 */
private suspend fun BdkTransaction.isUtxoConsolidation(
  myWallet: BdkWallet,
  amountSent: BitcoinMoney,
): Boolean =
  !amountSent.isZero &&
    output().isNotEmpty() &&
    output().all { myWallet.isMine(it.scriptPubkey).result.get() ?: false }

private val BdkBlockTime.blockTime: BlockTime
  get() =
    BlockTime(
      height = height,
      timestamp = timestamp
    )
