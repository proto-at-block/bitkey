package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkAddress
import build.wallet.bdk.bindings.BdkTxBuilder
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.wallet.CoinSelectionStrategy
import build.wallet.money.BitcoinMoney
import build.wallet.money.BitcoinMoney.Companion.sats
import com.ionspin.kotlin.bignum.integer.toBigInteger

/**
 * Returns UTXOs value as [BitcoinMoney].
 */
val BdkTxOut.bitcoinAmount: BitcoinMoney
  get() = sats(value.toBigInteger())

/**
 * Returns UTXOs value as [BitcoinMoney].
 */
val BdkUtxo.bitcoinAmount: BitcoinMoney
  get() = txOut.bitcoinAmount

val BdkUtxo.transactionId: BitcoinTransactionId
  get() = BitcoinTransactionId(outPoint.txid)

/**
 * Type-safe builder function for setting the absolute fee amount.
 */
fun BdkTxBuilder.feeAbsolute(amount: BitcoinMoney): BdkTxBuilder =
  feeAbsolute(amount.fractionalUnitValue.longValue())

/**
 * Type-safe builder function for setting the absolute fee amount.
 */
fun BdkTxBuilder.feeRate(rate: FeeRate): BdkTxBuilder = feeRate(satPerVbyte = rate.satsPerVByte)

/**
 * Applies the given [FeePolicy] to the transaction builder.
 */
fun BdkTxBuilder.feePolicy(policy: FeePolicy): BdkTxBuilder =
  when (policy) {
    is FeePolicy.Absolute -> feeAbsolute(policy.fee.amount)
    is FeePolicy.Rate -> feeRate(policy.feeRate)
    is FeePolicy.MinRelayRate -> this
  }

fun BdkTxBuilder.destination(
  bdkAddress: BdkAddress,
  amount: BitcoinTransactionSendAmount,
) = when (amount) {
  is BitcoinTransactionSendAmount.ExactAmount -> addRecipient(
    script = bdkAddress.scriptPubkey(),
    amount = amount.money.fractionalUnitValue
  )
  is BitcoinTransactionSendAmount.SendAll -> drainTo(address = bdkAddress).drainWallet()
}

fun BdkTxBuilder.coinSelectionStrategy(coinSelectionStrategy: CoinSelectionStrategy) =
  when (coinSelectionStrategy) {
    CoinSelectionStrategy.Default -> this
    is CoinSelectionStrategy.Preselected -> addUtxos(coinSelectionStrategy.inputs.map { it.outpoint })
    is CoinSelectionStrategy.Strict -> addUtxos(coinSelectionStrategy.inputs.map { it.outpoint })
      .manuallySelectedOnly()
  }
