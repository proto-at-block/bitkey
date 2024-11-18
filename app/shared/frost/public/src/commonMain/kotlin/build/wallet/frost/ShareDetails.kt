package build.wallet.frost

/**
 * Details about a customer's secret share.
 */
interface ShareDetails {
  val secretShare: List<UByte>
  val keyCommitments: KeyCommitments
}
