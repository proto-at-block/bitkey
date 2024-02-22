package build.wallet.recovery.socrec

import app.cash.turbine.Turbine
import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.InvitationFake
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.ProtectedCustomerFake
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.f8e.socrec.SocRecRelationshipsFake
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class SocRecRelationshipsRepositoryMock(
  turbine: (String) -> Turbine<Any>,
) : SocRecRelationshipsRepository {
  val launchSyncCalls = turbine("SocRecRelationshipsRepository launchSync calls")
  val syncCalls = turbine("SocRecRelationshipsRepository syncRelationships calls")

  var getSyncedSocRecRelationshipsResult: Result<SocRecRelationships, Error> =
    Ok(SocRecRelationshipsFake)

  override suspend fun syncLoop(account: Account) {
    launchSyncCalls.add(Unit)
  }

  override suspend fun syncRelationships(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<SocRecRelationships, NetworkingError> {
    syncCalls.add(Unit)

    return Ok(SocRecRelationshipsFake)
  }

  var relationshipsFlow =
    MutableStateFlow(
      SocRecRelationshipsFake
    )

  override val relationships = relationshipsFlow

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
  ): Result<Invitation, Error> {
    createInvitationCalls.add(Unit)
    return Ok(InvitationFake)
  }

  override suspend fun refreshInvitation(
    account: FullAccount,
    relationshipId: String,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Invitation, Error> {
    return Ok(InvitationFake)
  }

  private val defaultRetrieveInvitationResult:
    Result<Invitation, F8eError<RetrieveTrustedContactInvitationErrorCode>> =
    Ok(InvitationFake)

  var retrieveInvitationResult = defaultRetrieveInvitationResult

  override suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
  ): Result<Invitation, F8eError<RetrieveTrustedContactInvitationErrorCode>> {
    return retrieveInvitationResult
  }

  private val defaultAcceptInvitationResult:
    Result<ProtectedCustomer, F8eError<AcceptTrustedContactInvitationErrorCode>> =
    Ok(ProtectedCustomerFake)
  var acceptInvitationResult = defaultAcceptInvitationResult

  override suspend fun acceptInvitation(
    account: Account,
    invitation: Invitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    trustedContactIdentityKey: TrustedContactIdentityKey,
  ): Result<ProtectedCustomer, F8eError<AcceptTrustedContactInvitationErrorCode>> {
    return acceptInvitationResult
  }

  override suspend fun clear(): Result<Unit, Error> {
    getSyncedSocRecRelationshipsResult = Ok(SocRecRelationshipsFake)
    acceptInvitationResult = defaultAcceptInvitationResult
    retrieveInvitationResult = defaultRetrieveInvitationResult
    return Ok(Unit)
  }
}
