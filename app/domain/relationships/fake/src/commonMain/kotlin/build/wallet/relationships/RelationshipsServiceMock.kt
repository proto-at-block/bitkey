package build.wallet.relationships

import app.cash.turbine.Turbine
import bitkey.auth.AuthTokenScope
import bitkey.relationships.Relationships
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.bitkey.relationships.*
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.relationships.RelationshipsFake
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class RelationshipsServiceMock(
  turbine: (String) -> Turbine<Any>,
) : RelationshipsService {
  val syncCalls = turbine("RelationshipsService syncRelationships calls")

  private val defaultSyncAndVerifyRelationshipsResult: Result<Relationships, Error> = Ok(
    RelationshipsFake
  )
  var syncAndVerifyRelationshipsResult: Result<Relationships, Error> =
    defaultSyncAndVerifyRelationshipsResult

  override suspend fun syncAndVerifyRelationships(
    accountId: AccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>?,
    hwAuthPublicKey: HwAuthPublicKey?,
  ): Result<Relationships, Error> {
    syncCalls.add(Unit)

    return syncAndVerifyRelationshipsResult
  }

  var relationshipsFlow =
    MutableStateFlow(
      RelationshipsFake
    )

  override val relationships = relationshipsFlow

  override suspend fun getRelationshipsWithoutSyncing(
    accountId: AccountId,
  ): Result<Relationships, Error> {
    return Ok(relationshipsFlow.value)
  }

  val removeRelationshipWithoutSyncingCalls = turbine("removeRelationshipWithoutSyncing calls")

  override suspend fun removeRelationshipWithoutSyncing(
    accountId: AccountId,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, Error> {
    removeRelationshipWithoutSyncingCalls.add(relationshipId)
    return Ok(Unit)
  }

  val removeRelationshipCalls = turbine("removeRelationship calls")

  override suspend fun removeRelationship(
    account: Account,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, Error> {
    removeRelationshipCalls.add(relationshipId)
    return Ok(Unit)
  }

  val createInvitationCalls = turbine("createInvitation calls")

  override suspend fun createInvitation(
    account: FullAccount,
    trustedContactAlias: TrustedContactAlias,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    roles: Set<TrustedContactRole>,
  ): Result<OutgoingInvitation, Error> {
    createInvitationCalls.add(Unit)
    return Ok(OutgoingInvitationFake)
  }

  override suspend fun refreshInvitation(
    account: FullAccount,
    relationshipId: String,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<OutgoingInvitation, Error> {
    return Ok(OutgoingInvitationFake)
  }

  private val defaultRetrieveInvitationResult:
    Result<IncomingInvitation, RetrieveInvitationCodeError> =
    Ok(IncomingRecoveryContactInvitationFake)

  var retrieveInvitationResult = defaultRetrieveInvitationResult

  override suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
  ): Result<IncomingInvitation, RetrieveInvitationCodeError> {
    return retrieveInvitationResult
  }

  private val defaultAcceptInvitationResult:
    Result<ProtectedCustomer, AcceptInvitationCodeError> =
    Ok(ProtectedCustomerFake)
  var acceptInvitationResult = defaultAcceptInvitationResult

  override suspend fun acceptInvitation(
    account: Account,
    invitation: IncomingInvitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
    inviteCode: String,
  ): Result<ProtectedCustomer, AcceptInvitationCodeError> {
    return acceptInvitationResult
  }

  private val defaultPromoCodeResult:
    Result<PromotionCode?, RetrieveInvitationPromotionCodeError> =
    Ok(PromotionCode("fake-promotion-code"))

  var promoCodeResult = defaultPromoCodeResult

  override suspend fun retrieveInvitationPromotionCode(
    account: Account,
    invitationCode: String,
  ): Result<PromotionCode?, RetrieveInvitationPromotionCodeError> {
    return promoCodeResult
  }

  override suspend fun clear(): Result<Unit, Error> {
    acceptInvitationResult = defaultAcceptInvitationResult
    retrieveInvitationResult = defaultRetrieveInvitationResult
    syncAndVerifyRelationshipsResult = defaultSyncAndVerifyRelationshipsResult
    relationshipsFlow.value = RelationshipsFake
    return Ok(Unit)
  }
}
