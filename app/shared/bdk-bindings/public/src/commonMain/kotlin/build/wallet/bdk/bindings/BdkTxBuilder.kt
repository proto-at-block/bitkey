package build.wallet.bdk.bindings

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * A transaction builder.
 *
 * This is a builder for a transaction. It allows to add recipients, set the fee rate, and other
 * parameters. It is used to create a transaction that can be signed and broadcasted.
 */
interface BdkTxBuilder {
  /**
   * Add a recipient to the transaction.
   */
  fun addRecipient(
    script: BdkScript,
    amount: BigInteger,
  ): BdkTxBuilder

  /**
   * Set the fee rate for the transaction.
   */
  fun feeRate(satPerVbyte: Float): BdkTxBuilder

  /**
   * Set the absolute fee for the transaction.
   */
  fun feeAbsolute(fee: Long): BdkTxBuilder

  /**
   * Add the list of outpoints to the internal list of UTXOs that must be spent.
   *
   * If an error occurs while adding any of the UTXOs then none of them are added and the error is thrown.
   */
  fun addUtxos(utxos: List<BdkOutPoint>): BdkTxBuilder

  /**
   * Drain the wallet to the given address.
   */
  fun drainTo(address: BdkAddress): BdkTxBuilder

  /**
   * Spend all available inputs.
   */
  fun drainWallet(): BdkTxBuilder

  /**
   * Enable RBF for the transaction.
   */
  fun enableRbf(): BdkTxBuilder

  /*
   * Build the transaction.
   */
  fun finish(wallet: BdkWallet): BdkResult<BdkTxBuilderResult>
}
