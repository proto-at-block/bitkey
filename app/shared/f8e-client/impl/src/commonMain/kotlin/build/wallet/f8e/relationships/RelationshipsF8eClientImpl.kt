package build.wallet.f8e.relationships

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.AWAITING_VERIFY
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import build.wallet.f8e.error.toF8eError
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.relationships.models.*
import build.wallet.ktor.result.*
import build.wallet.mapUnit
import build.wallet.serialization.json.JsonEncodingError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.mapResult
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import kotlinx.collections.immutable.toImmutableList
import okio.ByteString

class RelationshipsF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : RelationshipsF8eClient {
  override suspend fun getRelationships(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Relationships, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        authTokenScope = AuthTokenScope.Recovery
      )
      .bodyResult<GetRecoveryRelationshipsResponseBody> {
        get("/api/accounts/${accountId.serverId}/relationships") {
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
  ): Result<Invitation, NetworkingError> {
    return f8eHttpClient.authenticated(
      f8eEnvironment = account.config.f8eEnvironment,
      accountId = account.accountId,
      hwFactorProofOfPossession = hardwareProofOfPossession
    )
      .bodyResult<CreateRelationshipInvitationResponseBody> {
        post("/api/accounts/${account.accountId.serverId}/relationships") {
          withDescription("Create relationship invitation")
          setRedactedBody(
            CreateRelationshipInvitationRequestBody(
              trustedContactAlias = trustedContactAlias,
              protectedCustomerEnrollmentPakeKey = protectedCustomerEnrollmentPakeKey,
              roles = roles.toList()
            )
          )
        }
      }.map {
        it.invitation.toInvitation()
      }
  }

  override suspend fun refreshInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    relationshipId: String,
  ): Result<Invitation, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        hwFactorProofOfPossession = hardwareProofOfPossession
      ).bodyResult<RefreshTrustedContactResponseBody> {
        put(
          "/api/accounts/${account.accountId.serverId}/recovery/relationships/$relationshipId"
        ) {
          withDescription("Refresh Invitation")
          setRedactedBody(RefreshTrustedContactRequestBody())
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
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        hwFactorProofOfPossession = hardwareProofOfPossession,
        authTokenScope = authTokenScope
      )
      .catching {
        delete(
          "/api/accounts/${accountId.serverId}/recovery/relationships/$relationshipId"
        ) {
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
        .authenticated(
          f8eEnvironment = f8eEnvironment,
          accountId = accountId,
          authTokenScope = AuthTokenScope.Global
        )
        .bodyResult<EndorseTrustedContactsResponseBody> {
          put("/api/accounts/${accountId.serverId}/recovery/relationships") {
            withDescription("Endorse trusted contacts")
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
  ): Result<IncomingInvitation, F8eError<RetrieveTrustedContactInvitationErrorCode>> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        authTokenScope = AuthTokenScope.Recovery
      )
      .bodyResult<RetrieveTrustedContactInvitationResponseBody> {
        get(
          "/api/accounts/${account.accountId.serverId}/recovery/relationship-invitations/$invitationCode"
        ) {
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
      .authenticated(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        authTokenScope = AuthTokenScope.Recovery
      )
      .bodyResult<AcceptTrustedContactInvitationResponseBody> {
        put(
          "/api/accounts/${account.accountId.serverId}/recovery/relationships/${invitation.relationshipId}"
        ) {
          withDescription("Accept Trusted Contact invitation")
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
    protectedCustomerEnrollmentPakeKey = protectedCustomerEnrollmentPakePubkey
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
