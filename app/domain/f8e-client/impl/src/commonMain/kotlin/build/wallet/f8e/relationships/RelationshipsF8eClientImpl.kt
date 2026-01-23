package build.wallet.f8e.relationships

import bitkey.auth.AuthTokenScope
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import bitkey.f8e.error.code.CreateTrustedContactInvitationErrorCode
import bitkey.f8e.error.code.F8eClientErrorCode
import bitkey.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import bitkey.f8e.error.toF8eError
import bitkey.relationships.Relationships
import bitkey.serialization.json.JsonEncodingError
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.AWAITING_VERIFY
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SealedData
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Impl
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.client.plugins.withHardwareFactor
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.relationships.models.*
import build.wallet.ktor.result.*
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.mapResult
import io.ktor.client.request.*
import kotlinx.collections.immutable.toImmutableList
import okio.ByteString

@Impl
@BitkeyInject(AppScope::class)
class RelationshipsF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : RelationshipsF8eClient {
  override suspend fun getRelationships(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Relationships, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<GetRecoveryRelationshipsResponseBody> {
        get("/api/accounts/${accountId.serverId}/relationships") {
          withEnvironment(f8eEnvironment)
          withAccountId(accountId, AuthTokenScope.Recovery)
          withDescription("Fetch relationships")
        }
      }
      .map {
        Relationships(
          invitations = it.invitations.map { invite -> invite.toInvitation() },
          endorsedTrustedContacts = it.endorsedEndorsedTrustedContacts.map { endorsement ->
            endorsement.toEndorsedTrustedContact()
          },
          unendorsedTrustedContacts = it.unendorsedTrustedContacts,
          protectedCustomers = it.customers.toImmutableList()
        )
      }
  }

  override suspend fun createInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
    protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
    roles: Set<TrustedContactRole>,
  ): Result<Invitation, F8eError<CreateTrustedContactInvitationErrorCode>> {
    return f8eHttpClient.authenticated()
      .bodyResult<CreateRelationshipInvitationResponseBody> {
        post("/api/accounts/${account.accountId.serverId}/relationships") {
          withDescription("Create relationship invitation")
          withEnvironment(account.config.f8eEnvironment)
          withAccountId(account.accountId)
          withHardwareFactor(hardwareProofOfPossession)
          setRedactedBody(
            CreateRelationshipInvitationRequestBody(
              trustedContactAlias = trustedContactAlias,
              protectedCustomerEnrollmentPakeKey = protectedCustomerEnrollmentPakeKey,
              roles = roles.toList()
            )
          )
        }
      }
      .map { it.invitation.toInvitation() }
      .mapError { it.toF8eError<CreateTrustedContactInvitationErrorCode>() }
  }

  override suspend fun refreshInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    relationshipId: String,
  ): Result<Invitation, NetworkingError> {
    return f8eHttpClient.authenticated()
      .bodyResult<RefreshTrustedContactResponseBody> {
        put(
          "/api/accounts/${account.accountId.serverId}/recovery/relationships/$relationshipId"
        ) {
          withEnvironment(account.config.f8eEnvironment)
          withAccountId(account.accountId)
          withHardwareFactor(hardwareProofOfPossession)
          withDescription("Refresh Invitation")
          setRedactedBody(RefreshTrustedContactRequestBody(action = "Reissue"))
        }
      }.map {
        it.invitation.toInvitation()
      }
  }

  override suspend fun removeRelationship(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .catching {
        delete(
          "/api/accounts/${accountId.serverId}/recovery/relationships/$relationshipId"
        ) {
          withEnvironment(f8eEnvironment)
          withAccountId(accountId, authTokenScope)
          hardwareProofOfPossession?.run(::withHardwareFactor)
          withDescription("Delete relationship")
          setRedactedBody(EmptyRequestBody)
        }
      }
      .mapUnit()
  }

  override suspend fun endorseTrustedContacts(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    endorsements: List<TrustedContactEndorsement>,
  ): Result<Unit, Error> =
    coroutineBinding {
      val f8eEndorsements = endorsements.mapResult { it.body() }.bind()

      f8eHttpClient
        .authenticated()
        .bodyResult<EndorseTrustedContactsResponseBody> {
          put("/api/accounts/${accountId.serverId}/recovery/relationships") {
            withDescription("Endorse trusted contacts")
            withEnvironment(f8eEnvironment)
            withAccountId(accountId, AuthTokenScope.Global)
            setRedactedBody(EndorseTrustedContactsRequestBody(f8eEndorsements))
          }
        }
        .mapUnit()
        .bind()
    }

  private suspend fun TrustedContactEndorsement.body(): Result<EndorsementBody, JsonEncodingError> =
    coroutineBinding {
      EndorsementBody(
        relationshipId = relationshipId.value,
        delegatedDecryptionKeyCertificate = keyCertificate.encode().bind()
      )
    }

  override suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
    expectedRole: TrustedContactRole?,
  ): Result<IncomingInvitation, F8eError<RetrieveTrustedContactInvitationErrorCode>> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<RetrieveTrustedContactInvitationResponseBody> {
        get(
          "/api/accounts/${account.accountId.serverId}/recovery/relationship-invitations/$invitationCode"
        ) {
          expectedRole?.let { role ->
            parameter("expected_role", role.key)
          }
          withEnvironment(account.config.f8eEnvironment)
          withAccountId(account.accountId, AuthTokenScope.Recovery)
          withDescription("Retrieve Trusted Contact invitation")
        }
      }
      .map { it.invitation.toIncomingInvitation(invitationCode) }
      .mapError { it.toF8eError<RetrieveTrustedContactInvitationErrorCode>() }
  }

  override suspend fun acceptInvitation(
    account: Account,
    invitation: IncomingInvitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    trustedContactEnrollmentPakeKey: PublicKey<TrustedContactEnrollmentPakeKey>,
    enrollmentPakeConfirmation: ByteString,
    sealedDelegateDecryptionKeyCipherText: XCiphertext,
  ): Result<ProtectedCustomer, F8eError<AcceptTrustedContactInvitationErrorCode>> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<AcceptTrustedContactInvitationResponseBody> {
        put(
          "/api/accounts/${account.accountId.serverId}/recovery/relationships/${invitation.relationshipId}"
        ) {
          withDescription("Accept Trusted Contact invitation")
          withEnvironment(account.config.f8eEnvironment)
          withAccountId(account.accountId, AuthTokenScope.Recovery)
          setRedactedBody(
            AcceptTrustedContactInvitationRequestBody(
              code = invitation.code,
              customerAlias = protectedCustomerAlias.alias,
              trustedContactEnrollmentPakeKey = trustedContactEnrollmentPakeKey,
              enrollmentPakeConfirmation = enrollmentPakeConfirmation,
              sealedDelegateDecryptionKey = sealedDelegateDecryptionKeyCipherText
            )
          )
        }
      }
      .map { it.customer }
      .mapError { it.toF8eError<AcceptTrustedContactInvitationErrorCode>() }
  }

  override suspend fun uploadSealedDelegatedDecryptionKeyData(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    sealedData: SealedData,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<EmptyResponseBody> {
        post("/api/accounts/${accountId.serverId}/recovery/backup") {
          withDescription("Post Backup")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId, AuthTokenScope.Global)
          setRedactedBody(
            UploadSealedDelegatedDecryptionKeyRequestBody(
              recoveryBackupMaterial = sealedData
            )
          )
        }
      }
      .mapUnit()
  }

  override suspend fun getSealedDelegatedDecryptionKeyData(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<SealedData, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<GetSealedDelegatedDecryptionKeyRequestBody> {
        get("/api/accounts/${accountId.serverId}/recovery/backup") {
          withDescription("Get Backup")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId, AuthTokenScope.Global)
        }
      }
      .map { it.recoveryBackupMaterial }
  }

  override suspend fun retrieveInvitationPromotionCode(
    account: Account,
    invitationCode: String,
  ): Result<PromotionCode?, F8eError<F8eClientErrorCode>> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<RetrieveInvitationPromotionCodeResponseBody> {
        get(
          "/api/accounts/${account.accountId.serverId}/recovery/relationship-invitations/$invitationCode/promotion-code"
        ) {
          withDescription("Retrieve invitation promotion code")
          withEnvironment(account.config.f8eEnvironment)
          withAccountId(account.accountId, AuthTokenScope.Recovery)
        }
      }
      .map { it.code?.let(::PromotionCode) }
      .mapError { it.toF8eError<F8eClientErrorCode>() }
  }
}

private fun F8eEndorsedTrustedContact.toEndorsedTrustedContact() =
  EndorsedTrustedContact(
    relationshipId = relationshipId,
    trustedContactAlias = trustedContactAlias,
    keyCertificate = keyCertificate,
    /**
     * The default auth state is [AWAITING_VERIFY] because the server does not store the auth state,
     * and the app cannot assume that the contact is verified.
     */
    authenticationState = AWAITING_VERIFY,
    roles = roles
  )

private fun RetrieveTrustedContactInvitation.toIncomingInvitation(invitationCode: String) =
  IncomingInvitation(
    relationshipId = relationshipId,
    code = invitationCode,
    protectedCustomerEnrollmentPakeKey = protectedCustomerEnrollmentPakePubkey,
    recoveryRelationshipRoles = recoveryRelationshipRoles,
    expiresAt = expiresAt
  )

private fun RelationshipInvitation.toInvitation() =
  Invitation(
    relationshipId = relationshipId,
    trustedContactAlias = trustedContactAlias,
    roles = roles,
    code = code,
    codeBitLength = codeBitLength,
    expiresAt = expiresAt
  )
