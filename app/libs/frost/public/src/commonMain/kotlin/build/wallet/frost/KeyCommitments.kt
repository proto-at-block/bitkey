package build.wallet.frost

/**
 * Public information about a customer's joint key.
 */
interface KeyCommitments {
  val vssCommitments: List<PublicKey>
  val aggregatePublicKey: PublicKey
}
