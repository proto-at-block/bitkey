package build.wallet.bdk.bindings

/**
 * Factory object that creates transaction builders to bump the fee of an existing transaction.
 */
interface BdkBumpFeeTxBuilderFactory {
  /**
   * Creates a new bump fee transaction builder.
   *
   * @param txid The transaction id of the transaction to bump the fee of.
   * @param feeRate The new fee rate to use for the bumped transaction.
   */
  fun bumpFeeTxBuilder(
    txid: String,
    feeRate: Float,
  ): BdkBumpFeeTxBuilder
}
