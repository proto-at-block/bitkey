package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BlockTime
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailDao
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logWarn
import build.wallet.money.BitcoinMoney
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Instant
import uniffi.bdk.Address
import uniffi.bdk.ChainPosition
import uniffi.bdk.LocalOutput
import uniffi.bdk.Script
import uniffi.bdk.TxDetails
import uniffi.bdk.TxIn
import uniffi.bdk.TxOut
import uniffi.bdk.Wallet

@BitkeyInject(AppScope::class)
class BdkTransactionMapperV2Impl(
  private val outgoingTransactionDetailDao: OutgoingTransactionDetailDao,
) : BdkTransactionMapperV2 {
  override suspend fun createTransaction(
    txDetails: TxDetails,
    wallet: Wallet,
    networkType: BitcoinNetworkType,
  ): BitcoinTransaction {
    // Sum of owned inputs of this transaction.
    // AS A RECEIVER
    //    This value is always 0, since you will not own any of the inputs.
    // AS A SENDER
    //    This value will not be 0, since you will have to put up your UTXOs as inputs.
    // AS A UTXO CONSOLIDATION
    //    This value will not be 0, since you are "sending" to yourself.
    val sent = BitcoinMoney.sats(txDetails.sent.toSat())

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
    val received = BitcoinMoney.sats(txDetails.received.toSat())

    // If sent amount is zero, that means this transaction is one where you only received.
    val isZeroSumTransaction = sent.isZero && !received.isZero

    val fee = txDetails.fee?.let { BitcoinMoney.sats(it.toSat()) }
    val transactionWeight = txDetails.tx.weight()
    val vsize = txDetails.tx.vsize()

    val isUtxoConsolidation = txDetails.tx.isUtxoConsolidation(wallet, sent)

    val total = if (isZeroSumTransaction || isUtxoConsolidation) {
      received + (fee ?: BitcoinMoney.zero())
    } else {
      sent - received
    }

    // If this a receive, the subtotal is just how much you received.
    val subtotal = if (isZeroSumTransaction || isUtxoConsolidation) {
      received
    } else {
      total - (fee ?: BitcoinMoney.zero())
    }

    val transactionType = when {
      sent.isZero -> TransactionType.Incoming
      isUtxoConsolidation -> TransactionType.UtxoConsolidation
      else -> TransactionType.Outgoing
    }

    val txid = txDetails.txid.toString()

    return BitcoinTransaction(
      id = txid,
      recipientAddress = txDetails.tx.findRecipientAddress(wallet, networkType, transactionType),
      broadcastTime = outgoingTransactionDetailDao.broadcastTimeForTransaction(transactionId = txid),
      estimatedConfirmationTime = outgoingTransactionDetailDao.confirmationTimeForTransaction(transactionId = txid),
      confirmationStatus = txDetails.chainPosition.toConfirmationStatus(),
      subtotal = subtotal,
      total = total,
      fee = fee,
      vsize = vsize,
      weight = transactionWeight,
      transactionType = transactionType,
      inputs = txDetails.tx.input().map { it.toBdkTxIn() }.toImmutableList(),
      outputs = txDetails.tx.output().map { it.toBdkTxOut() }.toImmutableList()
    )
  }

  override fun createUtxo(localOutput: LocalOutput): BdkUtxo {
    return BdkUtxo(
      outPoint = BdkOutPoint(
        txid = localOutput.outpoint.txid.toString(),
        vout = localOutput.outpoint.vout
      ),
      txOut = localOutput.txout.toBdkTxOut(),
      isSpent = localOutput.isSpent
    )
  }

  /**
   * Converts BDK v2's [ChainPosition] to our domain [ConfirmationStatus].
   */
  private fun ChainPosition.toConfirmationStatus(): ConfirmationStatus {
    return when (this) {
      is ChainPosition.Confirmed -> ConfirmationStatus.Confirmed(
        blockTime = BlockTime(
          height = confirmationBlockTime.blockId.height.toLong(),
          timestamp = Instant.fromEpochSeconds(confirmationBlockTime.confirmationTime.toLong())
        )
      )
      is ChainPosition.Unconfirmed -> ConfirmationStatus.Pending
    }
  }

  /**
   * Finds the recipient address for a transaction based on its type.
   */
  private fun uniffi.bdk.Transaction.findRecipientAddress(
    wallet: Wallet,
    networkType: BitcoinNetworkType,
    transactionType: TransactionType,
  ): BitcoinAddress? {
    val outputs = output()
    if (outputs.isEmpty()) return null

    // Find the TxOut based on transaction type:
    // - For incoming/consolidation: find output that IS mine
    // - For outgoing: find output that is NOT mine
    val addressTxOut = outputs.firstOrNull { txOut ->
      val isMine = wallet.isMine(txOut.scriptPubkey)
      when (transactionType) {
        TransactionType.Incoming, TransactionType.UtxoConsolidation -> isMine
        TransactionType.Outgoing -> !isMine
      }
    } ?: return null

    // BDK/uniffi FFI calls throw generic exceptions without specific subtypes
    @Suppress("TooGenericExceptionCaught")
    return try {
      val address = Address.fromScript(addressTxOut.scriptPubkey, networkType.bdkNetworkV2)
      BitcoinAddress(address.toString())
    } catch (e: Exception) {
      logWarn(throwable = e) { "Failed to parse recipient address from script" }
      null
    }
  }

  /**
   * Converts BDK v2's [TxIn] to our legacy [BdkTxIn] type.
   */
  private fun TxIn.toBdkTxIn(): BdkTxIn {
    return BdkTxIn(
      outpoint = BdkOutPoint(
        txid = previousOutput.txid.toString(),
        vout = previousOutput.vout
      ),
      sequence = sequence,
      witness = witness.map { bytes -> bytes.map { it.toUByte() } }
    )
  }

  /**
   * Converts BDK v2's [TxOut] to our legacy [BdkTxOut] type.
   */
  private fun TxOut.toBdkTxOut(): BdkTxOut {
    return BdkTxOut(
      value = value.toSat(),
      scriptPubkey = scriptPubkey.toBdkScript()
    )
  }

  /**
   * Converts BDK v2's [Script] to our legacy [BdkScript] type.
   */
  private fun Script.toBdkScript(): BdkScript {
    return object : BdkScript {
      override val rawOutputScript: List<UByte> = toBytes().map { it.toUByte() }
    }
  }
}

/**
 * Indicates whether a transaction is considered a UTXO consolidation; that is, whether all outputs
 * have a destination of the wallet.
 */
private fun uniffi.bdk.Transaction.isUtxoConsolidation(
  wallet: Wallet,
  amountSent: BitcoinMoney,
): Boolean =
  !amountSent.isZero &&
    output().isNotEmpty() &&
    output().all { wallet.isMine(it.scriptPubkey) }
