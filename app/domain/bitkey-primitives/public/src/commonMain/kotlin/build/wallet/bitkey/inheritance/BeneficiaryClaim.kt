package build.wallet.bitkey.inheritance

import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.encrypt.XCiphertext
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.datetime.Instant

/**
 * Inheritance Claim as viewed from the beneficiary's side
 */
sealed interface BeneficiaryClaim : InheritanceClaim {
  /**
   * Inheritance claims that have started, but not passed their delay period.
   */
  data class PendingClaim(
    override val claimId: InheritanceClaimId,
    override val relationshipId: RelationshipId,
    val delayEndTime: Instant,
    val delayStartTime: Instant,
    @Redacted
    val authKeys: InheritanceClaimKeyset,
  ) : BeneficiaryClaim

  /**
   * Inheritance claims that have been cancelled before being completed.
   */
  data class CanceledClaim(
    override val claimId: InheritanceClaimId,
    override val relationshipId: RelationshipId,
  ) : BeneficiaryClaim

  /**
   * Inheritance claims that have completed their delay period and have
   * subsequently been locked by the beneficiary.
   */
  data class LockedClaim(
    override val claimId: InheritanceClaimId,
    override val relationshipId: RelationshipId,
    @Redacted
    val sealedDek: XCiphertext,
    @Redacted
    val sealedMobileKey: XCiphertext,
    @Redacted
    val sealedDescriptor: XCiphertext?,
    @Redacted
    val benefactorKeyset: BenefactorDescriptorKeyset?,
    @Redacted
    val sealedServerRootXpub: XCiphertext?,
  ) : BeneficiaryClaim

  /**
   * Inheritance claims that have been signed and transferred.
   */
  data class CompleteClaim(
    override val claimId: InheritanceClaimId,
    override val relationshipId: RelationshipId,
  ) : BeneficiaryClaim

  /**
   * An inheritance claim that is in an unknown state.
   *
   * This type is included for forwards compatibility, and forces the
   * caller to handle claims that may be in an undefined state to this
   * application version without failing at deserialization.
   *
   * Construction of this type is hidden, as this type should only
   * be the result of deserialization.
   */
  data class UnknownStatus internal constructor(
    override val claimId: InheritanceClaimId,
    override val relationshipId: RelationshipId,
    val status: InheritanceClaimStatus,
  ) : BeneficiaryClaim
}
