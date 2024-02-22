package build.wallet.bitcoin.fees

/**
 * Represents different ways to specify fees for a transaction.
 */
sealed class FeePolicy {
  /** An absolute fee, in sats. */
  data class Absolute(val fee: Fee) : FeePolicy()

  /** A custom fee rate, in sats/vB. */
  data class Rate(val feeRate: FeeRate) : FeePolicy()

  /*
   * When passed to SpendingWallet, the underlying TxBuilder never calls .feeAbsolute or .feeRate.
   *
   * Hence, by default, BDK will then fall back to set feerate at 1sat/vB, which is the minimum fee
   * rate required for the transaction to be relayed in the mempool. Using this fee policy is useful
   * for constructing "dummy" PSBTs for [BitcoinTransactionFeeEstimator] to apply our own heuristic
   * for fee estimation.
   */
  data object MinRelayRate : FeePolicy()
}
