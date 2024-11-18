package build.wallet.bitkey.inheritance

import build.wallet.bitkey.relationships.RelationshipId
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Handles the polymorphic serialization of a benefactor's inheritance claim data.
 */
object BenefactorClaimSerializer : KSerializer<BenefactorClaim> {
  private val surrogateSerializer = BenefactorClaimSurrogate.serializer()
  override val descriptor: SerialDescriptor = surrogateSerializer.descriptor

  override fun deserialize(decoder: Decoder): BenefactorClaim {
    val surrogate = decoder.decodeSerializableValue(surrogateSerializer)
    return when (surrogate.status) {
      InheritanceClaimStatus.PENDING -> surrogate.asPendingClaim()
      InheritanceClaimStatus.CANCELED -> surrogate.asCancelledClaim()
      InheritanceClaimStatus.LOCKED -> surrogate.asLockedClaim()
      InheritanceClaimStatus.COMPLETE -> surrogate.asCompleteClaim()
      else -> surrogate.asUnknownState()
    }
  }

  override fun serialize(
    encoder: Encoder,
    value: BenefactorClaim,
  ) {
    encoder.encodeSerializableValue(surrogateSerializer, BenefactorClaimSurrogate(value))
  }

  /**
   * Surrogate class containing any data needed to serialize a benefactor claim.
   */
  @Serializable
  private class BenefactorClaimSurrogate(
    @SerialName("status")
    val status: InheritanceClaimStatus,
    @SerialName("id")
    val claimId: InheritanceClaimId,
    @SerialName("recovery_relationship_id")
    val relationshipId: RelationshipId,
    @SerialName("delay_end_time")
    val delayEndTime: Instant? = null,
    @SerialName("delay_start_time")
    val delayStartTime: Instant? = null,
  ) {
    constructor(claim: BenefactorClaim) : this(
      status = when (claim) {
        is BenefactorClaim.PendingClaim -> InheritanceClaimStatus.PENDING
        is BenefactorClaim.CanceledClaim -> InheritanceClaimStatus.CANCELED
        is BenefactorClaim.LockedClaim -> InheritanceClaimStatus.LOCKED
        is BenefactorClaim.CompleteClaim -> InheritanceClaimStatus.COMPLETE
        is BenefactorClaim.UnknownStatus -> error("Cannot serialize claim with unknown state")
      },
      claimId = claim.claimId,
      relationshipId = claim.relationshipId,
      delayEndTime = (claim as? BenefactorClaim.PendingClaim)?.delayEndTime,
      delayStartTime = (claim as? BenefactorClaim.PendingClaim)?.delayStartTime
    )

    fun asPendingClaim() =
      BenefactorClaim.PendingClaim(
        claimId = claimId,
        relationshipId = relationshipId,
        delayEndTime = delayEndTime!!,
        delayStartTime = delayStartTime!!
      )

    fun asCancelledClaim() =
      BenefactorClaim.CanceledClaim(
        claimId = claimId,
        relationshipId = relationshipId
      )

    fun asLockedClaim() =
      BenefactorClaim.LockedClaim(
        claimId = claimId,
        relationshipId = relationshipId
      )

    fun asCompleteClaim() =
      BenefactorClaim.CompleteClaim(
        claimId = claimId,
        relationshipId = relationshipId
      )

    fun asUnknownState() =
      BenefactorClaim.UnknownStatus(
        claimId = claimId,
        relationshipId = relationshipId,
        status = status
      )
  }
}
