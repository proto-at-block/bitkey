package build.wallet.bitkey.inheritance

import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.encrypt.XCiphertext
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Handles the polymorphic serialization of a beneficiary's inheritance claim data.
 */
object BeneficiaryClaimSerializer : KSerializer<BeneficiaryClaim> {
  private val surrogateSerializer = BeneficiaryClaimSurrogate.serializer()
  override val descriptor: SerialDescriptor = surrogateSerializer.descriptor

  override fun deserialize(decoder: Decoder): BeneficiaryClaim {
    val surrogate = decoder.decodeSerializableValue(surrogateSerializer)
    return when (surrogate.status) {
      InheritanceClaimStatus.PENDING -> surrogate.asPendingClaim()
      InheritanceClaimStatus.CANCELED -> surrogate.asCancelledClaim()
      InheritanceClaimStatus.LOCKED -> surrogate.asLockedClaim()
      else -> surrogate.asUnknownState()
    }
  }

  override fun serialize(
    encoder: Encoder,
    value: BeneficiaryClaim,
  ) {
    encoder.encodeSerializableValue(surrogateSerializer, BeneficiaryClaimSurrogate(value))
  }

  /**
   * Surrogate class containing any data needed to serialize a beneficiary claim.
   */
  @Serializable
  private class BeneficiaryClaimSurrogate(
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
    @SerialName("auth_keys")
    val authKeys: InheritanceClaimKeyset? = null,
    @SerialName("sealed_dek")
    val sealedDek: XCiphertext? = null,
    @SerialName("sealed_mobile_key")
    val sealedMobileKey: XCiphertext? = null,
  ) {
    constructor(claim: BeneficiaryClaim) : this(
      status = when (claim) {
        is BeneficiaryClaim.PendingClaim -> InheritanceClaimStatus.PENDING
        is BeneficiaryClaim.CanceledClaim -> InheritanceClaimStatus.CANCELED
        is BeneficiaryClaim.LockedClaim -> InheritanceClaimStatus.LOCKED
        is BeneficiaryClaim.UnknownStatus -> error("Cannot serialize claim with unknown state")
      },
      claimId = claim.claimId,
      relationshipId = claim.relationshipId,
      delayEndTime = (claim as? BeneficiaryClaim.PendingClaim)?.delayEndTime,
      delayStartTime = (claim as? BeneficiaryClaim.PendingClaim)?.delayStartTime,
      authKeys = (claim as? BeneficiaryClaim.PendingClaim)?.authKeys,
      sealedDek = (claim as? BeneficiaryClaim.LockedClaim)?.sealedDek,
      sealedMobileKey = (claim as? BeneficiaryClaim.LockedClaim)?.sealedMobileKey
    )

    fun asPendingClaim() =
      BeneficiaryClaim.PendingClaim(
        claimId = claimId,
        relationshipId = relationshipId,
        delayEndTime = delayEndTime!!,
        delayStartTime = delayStartTime!!,
        authKeys = authKeys!!
      )

    fun asCancelledClaim() =
      BeneficiaryClaim.CanceledClaim(
        claimId = claimId,
        relationshipId = relationshipId
      )

    fun asLockedClaim() =
      BeneficiaryClaim.LockedClaim(
        claimId = claimId,
        relationshipId = relationshipId,
        sealedDek = sealedDek!!,
        sealedMobileKey = sealedMobileKey!!
      )

    fun asUnknownState() =
      BeneficiaryClaim.UnknownStatus(
        claimId = claimId,
        relationshipId = relationshipId,
        status = status
      )
  }
}
