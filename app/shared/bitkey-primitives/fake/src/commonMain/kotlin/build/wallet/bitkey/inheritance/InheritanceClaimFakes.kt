package build.wallet.bitkey.inheritance

import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.encrypt.XCiphertext
import build.wallet.time.someInstant
import kotlin.time.Duration.Companion.days

val BeneficiaryPendingClaimFake = BeneficiaryClaim.PendingClaim(
  claimId = InheritanceClaimId("claim-benefactor-pending-id"),
  relationshipId = RelationshipId("relationship-benefactor-pending-id"),
  delayEndTime = someInstant.plus(180.days),
  delayStartTime = someInstant,
  authKeys = InheritanceClaimKeyset(
    appPubkey = "fake-app-pubkey",
    hardwarePubkey = "fake-hardware-pubkey"
  )
)

val BeneficiaryCanceledClaimFake =
  BeneficiaryClaim.CanceledClaim(
    claimId = InheritanceClaimId("claim-benefactor-canceled-id"),
    relationshipId = RelationshipId("relationship-benefactor-canceled-id")
  )

val BeneficiaryLockedClaimFake =
  BeneficiaryClaim.LockedClaim(
    claimId = InheritanceClaimId("claim-benefactor-locked-id"),
    relationshipId = RelationshipId("relationship-benefactor-locked-id"),
    sealedDek = XCiphertext("sealed-dek"),
    sealedMobileKey = XCiphertext("sealed-mobile-key")
  )

val BenefactorPendingClaimFake = BenefactorClaim.PendingClaim(
  claimId = InheritanceClaimId("claim-benefactor-pending-id"),
  relationshipId = RelationshipId("relationship-benefactor-pending-id"),
  delayEndTime = someInstant.plus(180.days),
  delayStartTime = someInstant
)

val BenefactorCanceledClaimFake = BenefactorClaim.CanceledClaim(
  claimId = InheritanceClaimId("claim-benefactor-canceled-id"),
  relationshipId = RelationshipId("relationship-benefactor-canceled-id")
)

val BenefactorLockedClaimFake = BenefactorClaim.LockedClaim(
  claimId = InheritanceClaimId("claim-benefactor-locked-id"),
  relationshipId = RelationshipId("relationship-benefactor-locked-id")
)
