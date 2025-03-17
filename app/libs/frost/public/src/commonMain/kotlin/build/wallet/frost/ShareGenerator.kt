package build.wallet.frost

/**
 * A stateful class used to progress through the FROST Distributed Key Generation
 * (DKG) protocol.
 */
interface ShareGenerator {
  /**
   * Generates data required to send to the Server as part of the FROST Distributed Key Generation
   * (DKG) protocol.
   */
  fun generate(): KeygenResult<UnsealedRequest>

  /**
   * Aggregates share package received from the Server to produce [ShareDetails], representing the
   * final output of a Distributed Key Generation (DKG).
   */
  fun aggregate(unsealedRequest: UnsealedRequest): KeygenResult<ShareDetails>

  /**
   * Encodes share details into a SealedResponse suitable for [aggregate].
   */
  fun encode(shareDetails: ShareDetails): KeygenResult<UnsealedRequest>
}
