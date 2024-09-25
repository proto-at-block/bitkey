package build.wallet.bdk.bindings

/**
 * Builder for a transaction that bumps the fee of an existing transaction.
 */
interface BdkBumpFeeTxBuilder {
  /**
   * Enable signalling RBF for the transaction.
   *
   * This will use the default nSequence value of `0xFFFFFFFD`.
   */
  fun enableRbf(): BdkBumpFeeTxBuilder

  /**
   * Explicitly tells the wallet that it is allowed to reduce the amount of the output matching this
   * scriptPubKey in order to bump the transaction fee. Without specifying this the wallet will
   * attempt to find a change output to shrink instead.
   *
   * Note that the output may shrink to below the dust limit and therefore be removed. If it is
   * preserved then it is currently not guaranteed to be in the same position as it was originally.
   *
   * Returns an error if scriptPubkey can’t be found among the recipients of the transaction we are bumping.
   */
  fun allowShrinking(script: BdkScript): BdkBumpFeeTxBuilder

  /**
   * Finish building the transaction and return the BIP174 partially signed transaction.
   */
  fun finish(wallet: BdkWallet): BdkResult<BdkPartiallySignedTransaction>
}
