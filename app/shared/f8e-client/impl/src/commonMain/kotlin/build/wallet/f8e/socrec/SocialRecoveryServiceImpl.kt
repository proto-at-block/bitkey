package build.wallet.f8e.socrec

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.IncomingInvitation
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentPakeKey
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.bitkey.socrec.StartSocialChallengeRequestTrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactEndorsement
import build.wallet.bitkey.socrec.TrustedContactEnrollmentPakeKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import build.wallet.f8e.error.toF8eError
import build.wallet.f8e.socrec.models.AcceptTrustedContactInvitationRequestBody
import build.wallet.f8e.socrec.models.AcceptTrustedContactInvitationResponseBody
import build.wallet.f8e.socrec.models.ChallengeVerificationResponse
import build.wallet.f8e.socrec.models.CreateTrustedContactInvitation
import build.wallet.f8e.socrec.models.CreateTrustedContactInvitationRequestBody
import build.wallet.f8e.socrec.models.CreateTrustedContactInvitationResponseBody
import build.wallet.f8e.socrec.models.EndorseTrustedContactsRequestBody
import build.wallet.f8e.socrec.models.EndorseTrustedContactsResponseBody
import build.wallet.f8e.socrec.models.EndorsementBody
import build.wallet.f8e.socrec.models.GetRecoveryRelationshipsResponseBody
import build.wallet.f8e.socrec.models.RefreshTrustedContactRequestBody
import build.wallet.f8e.socrec.models.RefreshTrustedContactResponseBody
import build.wallet.f8e.socrec.models.RespondToChallengeRequestBody
import build.wallet.f8e.socrec.models.RetrieveTrustedContactInvitation
import build.wallet.f8e.socrec.models.RetrieveTrustedContactInvitationResponseBody
import build.wallet.f8e.socrec.models.SocialChallengeResponseBody
import build.wallet.f8e.socrec.models.StartSocialChallengeRequestBody
import build.wallet.f8e.socrec.models.VerifyChallengeRequestBody
import build.wallet.f8e.socrec.models.VerifyChallengeResponseBody
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.catching
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import build.wallet.serialization.json.JsonEncodingError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.mapResult
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.collections.immutable.toImmutableList
import okio.ByteString

class SocialRecoveryServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : SocialRecoveryService {
  override suspend fun getRelationships(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
  ): Result<SocRecRelationships, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        hwFactorProofOfPossession = hardwareProofOfPossession,
        authTokenScope = AuthTokenScope.Recovery
      )
      .bodyResult<GetRecoveryRelationshipsResponseBody> {
        get("/api/accounts/${accountId.serverId}/recovery/relationships")
      }
      .map {
        SocRecRelationships(
          invitations = it.invitations.map { invite -> invite.toInvitation() },
          trustedContacts = it.endorsedTrustedContacts,
          unendorsedTrustedContacts = it.unendorsedTrustedContacts,
          protectedCustomers = it.customers.toImmutableList()
        )
      }
      .logNetworkFailure { "Failed to fetch relationships" }
  }

  override suspend fun createInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
    protectedCustomerEnrollmentPakeKey: ProtectedCustomerEnrollmentPakeKey,
  ): Result<Invitation, NetworkingError> {
    return f8eHttpClient.authenticated(
      f8eEnvironment = account.config.f8eEnvironment,
      accountId = account.accountId,
      hwFactorProofOfPossession = hardwareProofOfPossession
    )
      .bodyResult<CreateTrustedContactInvitationResponseBody> {
        post("/api/accounts/${account.accountId.serverId}/recovery/relationships") {
          setBody(
            CreateTrustedContactInvitationRequestBody(
              trustedContactAlias = trustedContactAlias,
              protectedCustomerEnrollmentPakeKey = protectedCustomerEnrollmentPakeKey
            )
          )
        }
      }.map {
        it.invitation.toInvitation()
      }.logNetworkFailure { "Failed to create invitation" }
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
          setBody(RefreshTrustedContactRequestBody())
        }
      }.map {
        it.invitation.toInvitation()
      }.logNetworkFailure {
        "Failed to refresh Invitation"
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
          setBody("{}")
        }
      }
      .map { Unit }
      .logNetworkFailure { "Failed to delete relationship" }
  }

  override suspend fun startChallenge(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    trustedContacts: List<StartSocialChallengeRequestTrustedContact>,
  ): Result<SocialChallenge, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = fullAccountId,
        authTokenScope = AuthTokenScope.Recovery
      )
      .bodyResult<SocialChallengeResponseBody> {
        post("/api/accounts/${fullAccountId.serverId}/recovery/social-challenges") {
          setBody(
            StartSocialChallengeRequestBody(
              trustedContacts = trustedContacts
            )
          )
        }
      }
      .logNetworkFailure { "Failed to start Social Recovery Challenge" }
      .map { it.challenge }
  }

  override suspend fun getSocialChallengeStatus(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    challengeId: String,
  ): Result<SocialChallenge, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = fullAccountId,
        authTokenScope = AuthTokenScope.Recovery
      )
      .bodyResult<SocialChallengeResponseBody> {
        get("/api/accounts/${fullAccountId.serverId}/recovery/social-challenges/$challengeId")
      }
      .logNetworkFailure { "Failed to fetch Social Recovery Challenge" }
      .map { it.challenge }
  }

  override suspend fun verifyChallenge(
    account: Account,
    recoveryRelationshipId: String,
    counter: Int,
  ): Result<ChallengeVerificationResponse, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        authTokenScope = AuthTokenScope.Recovery
      ).bodyResult<VerifyChallengeResponseBody> {
        post(
          "/api/accounts/${account.accountId.serverId}/recovery/verify-social-challenge"
        ) {
          setBody(
            VerifyChallengeRequestBody(
              recoveryRelationshipId = recoveryRelationshipId,
              code = counter
            )
          )
        }
      }.logNetworkFailure {
        "Failed to verify challenge code"
      }
      .map { it.challenge }
  }

  override suspend fun respondToChallenge(
    account: Account,
    socialChallengeId: String,
    trustedContactRecoveryPakePubkey: PublicKey,
    recoveryPakeConfirmation: ByteString,
    resealedDek: XCiphertext,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        authTokenScope = AuthTokenScope.Recovery
      ).bodyResult<Unit> {
        put(
          "/api/accounts/${account.accountId.serverId}/recovery/social-challenges/$socialChallengeId"
        ) {
          setBody(
            RespondToChallengeRequestBody(
              trustedContactRecoveryPakePubkey = trustedContactRecoveryPakePubkey,
              recoveryPakeConfirmation = recoveryPakeConfirmation,
              resealedDek = resealedDek
            )
          )
        }
      }.logNetworkFailure {
        "Failed to verify challenge code"
      }
  }

  override suspend fun endorseTrustedContacts(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    endorsements: List<TrustedContactEndorsement>,
  ): Result<Unit, Error> =
    binding {
      val f8eEndorsements = endorsements.mapResult { it.body() }.bind()

      f8eHttpClient
        .authenticated(
          f8eEnvironment = f8eEnvironment,
          accountId = accountId,
          authTokenScope = AuthTokenScope.Global
        )
        .bodyResult<EndorseTrustedContactsResponseBody> {
          put("/api/accounts/${accountId.serverId}/recovery/relationships") {
            setBody(EndorseTrustedContactsRequestBody(f8eEndorsements))
          }
        }
        .logNetworkFailure { "Failed to endorse trusted contacts" }
        .mapUnit()
        .bind()
    }

  private suspend fun TrustedContactEndorsement.body(): Result<EndorsementBody, JsonEncodingError> =
    binding {
      EndorsementBody(
        recoveryRelationshipId = recoveryRelationshipId.value,
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
        )
      }
      .logNetworkFailure { "Failed to retrieve Trusted Contact invitation" }
      .map { it.invitation.toIncomingInvitation(invitationCode) }
      .mapError { it.toF8eError<RetrieveTrustedContactInvitationErrorCode>() }
  }

  override suspend fun acceptInvitation(
    account: Account,
    invitation: IncomingInvitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    trustedContactEnrollmentPakeKey: TrustedContactEnrollmentPakeKey,
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
          "/api/accounts/${account.accountId.serverId}/recovery/relationships/${invitation.recoveryRelationshipId}"
        ) {
          setBody(
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
      .logNetworkFailure { "Failed to accept Trusted Contact invitation" }
      .map { it.customer }
      .mapError { it.toF8eError<AcceptTrustedContactInvitationErrorCode>() }
  }
}

private fun CreateTrustedContactInvitation.toInvitation() =
  Invitation(
    recoveryRelationshipId = recoveryRelationshipId,
    trustedContactAlias = trustedContactAlias,
    code = code,
    codeBitLength = codeBitLength,
    expiresAt = expiresAt
  )

private fun RetrieveTrustedContactInvitation.toIncomingInvitation(invitationCode: String) =
  IncomingInvitation(
    recoveryRelationshipId = recoveryRelationshipId,
    code = invitationCode,
    protectedCustomerEnrollmentPakeKey = ProtectedCustomerEnrollmentPakeKey(
      AppKey.fromPublicKey(protectedCustomerEnrollmentPakePubkey)
    )
  )
