package build.wallet.bitcoin.utxo

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.money.BitcoinMoney

/**
 * @param type describes the type of this UTXO consolidation.
 * @param targetAddress the address to which the consolidated UTXOs will be sent.
 * @param eligibleUtxoCount current number of unspent transaction outputs eligible for consolidation.
 * Has to be greater than 1 in order to consolidate.
 * @param balance wallet's balance that can be consolidated (total unspent).
 * @param consolidationCost estimated cost of consolidation transaction.
 * @param appSignedPsbt the PSBT for the consolidation that needs to be signed
 * with hardware and broadcasted in order to consolidate UTXOs.
 * @param transactionPriority the priority to be used for the consolidation transaction.
 * This effectively determines what fee will be used for the transaction and how fast
 * the transaction will be confirmed on blockchain.
 * @param walletHasUnconfirmedUtxos whether the wallet has UTXos from unconfirmed incoming
 * transactions. Mostly used to inform the user that consolidation will not include these
 * UTXOs.
 * @param walletExceedsMaxUtxoCount whether the wallet has more UTXOs than can be consolidated in
 * a single transaction
 * @param maxUtxoCount that maximum number of UTXOs that can be consolidated in a single transaction
 */
data class UtxoConsolidationParams(
  val type: UtxoConsolidationType,
  val targetAddress: BitcoinAddress,
  val eligibleUtxoCount: Int,
  val balance: BitcoinMoney,
  val consolidationCost: BitcoinMoney,
  val appSignedPsbt: Psbt,
  val transactionPriority: EstimatedTransactionPriority,
  val walletHasUnconfirmedUtxos: Boolean,
  val walletExceedsMaxUtxoCount: Boolean,
  val maxUtxoCount: Int,
) {
  init {
    require(eligibleUtxoCount > 1) { "eligibleUtxoCount must be greater than 1" }
  }
}
