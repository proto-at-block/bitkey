package build.wallet.bdk.bindings

/**
 * Builder for a transaction that bumps the fee of an existing transaction.
 */
interface BdkBumpFeeTxBuilder {
  /*
   * Enable signalling RBF for the transaction.
   *
   * This will use the default nSequence value of `0xFFFFFFFD`.
   */
  fun enableRbf(): BdkBumpFeeTxBuilder

  /**
   * Finish building the transaction and return the BIP174 partially signed transaction.
   */
  fun finish(wallet: BdkWallet): BdkResult<BdkPartiallySignedTransaction>
}
