package build.wallet.frost

interface ShareGenerator {
  /**
   * Generates data required to send to the Server as part of the FROST Distributed Key Generation
   * (DKG) protocol.
   */
  fun generate(): KeygenResult<SharePackage>

  /**
   * Aggregates share package received from the Server to produce [ShareDetails], representing the
   * final output of a Distributed Key Generation (DKG).
   */
  fun aggregate(
    peerSharePackage: SharePackage,
    peerKeyCommitments: KeyCommitments,
  ): KeygenResult<ShareDetails>
}
