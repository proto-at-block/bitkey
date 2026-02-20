package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.wallet.CoinSelectionStrategy
import uniffi.bdk.Script
import uniffi.bdk.TxBuilder

/**
 * Applies the given [FeePolicy] to the BDK v2 transaction builder.
 */
internal fun TxBuilder.feePolicy(policy: FeePolicy): TxBuilder =
  when (policy) {
    is FeePolicy.Absolute -> feeAbsolute(policy.fee.amount.toBdkV2Amount())
    is FeePolicy.Rate -> feeRate(policy.feeRate.toBdkV2FeeRate())
    is FeePolicy.MinRelayRate -> this
  }

/**
 * Adds a recipient with the specified amount to the BDK v2 transaction builder.
 */
internal fun TxBuilder.sendTo(
  recipientScript: Script,
  amount: BitcoinTransactionSendAmount,
): TxBuilder =
  when (amount) {
    is BitcoinTransactionSendAmount.ExactAmount ->
      addRecipient(recipientScript, amount.money.toBdkV2Amount())
    is BitcoinTransactionSendAmount.SendAll ->
      drainWallet().drainTo(recipientScript)
  }

/**
 * Applies the given [CoinSelectionStrategy] to the BDK v2 transaction builder.
 */
internal fun TxBuilder.coinSelectionStrategy(strategy: CoinSelectionStrategy): TxBuilder =
  when (strategy) {
    CoinSelectionStrategy.Default -> this
    is CoinSelectionStrategy.Preselected ->
      strategy.inputs.fold(this) { builder, txIn ->
        builder.addUtxo(txIn.outpoint.toOutPoint())
      }
    is CoinSelectionStrategy.Strict ->
      strategy.inputs.fold(this) { builder, txIn ->
        builder.addUtxo(txIn.outpoint.toOutPoint())
      }.manuallySelectedOnly()
  }

/**
 * Restricts coin selection to only the specified UTXOs.
 * Any other UTXOs in the wallet will be ignored during transaction building.
 */
internal fun TxBuilder.selectOnlyUtxos(utxos: Set<BdkUtxo>): TxBuilder =
  utxos.fold(this) { builder, utxo ->
    builder.addUtxo(utxo.outPoint.toOutPoint())
  }.manuallySelectedOnly()
