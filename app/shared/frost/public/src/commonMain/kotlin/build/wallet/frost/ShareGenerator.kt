package build.wallet.frost

/**
 * A stateful class used to progress through the FROST Distributed Key Generation
 * (DKG) protocol.
 *
 * Get a new [ShareGenerator] by using [ShareGeneratorFactory].
 */
interface ShareGenerator {
  /**
   * Generates data required to send to the Server as part of the FROST Distributed Key Generation
   * (DKG) protocol.
   */
  fun generate(): KeygenResult<SealedRequest>

  /**
   * Aggregates share package received from the Server to produce [ShareDetails], representing the
   * final output of a Distributed Key Generation (DKG).
   */
  fun aggregate(sealedRequest: SealedRequest): KeygenResult<ShareDetails>

  /**
   * Encodes share details into a SealedResponse suitable for [aggregate].
   */
  fun encode(shareDetails: ShareDetails): KeygenResult<SealedRequest>
}
