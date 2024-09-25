package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.money.BitcoinMoney
import build.wallet.money.BitcoinMoney.Companion.sats
import com.ionspin.kotlin.bignum.integer.toBigInteger

/**
 * Returns UTXOs value as [BitcoinMoney].
 */
val BdkUtxo.bitcoinAmount: BitcoinMoney
  get() = sats(txOut.value.toBigInteger())

val BdkUtxo.transactionId: BitcoinTransactionId
  get() = BitcoinTransactionId(outPoint.txid)
