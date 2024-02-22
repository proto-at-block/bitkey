package build.wallet.f8e.socrec

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
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
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.collections.immutable.toImmutableList

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
          trustedContacts = it.trustedContacts,
          protectedCustomers = it.customers.toImmutableList()
        )
      }
      .logNetworkFailure { "Failed to fetch relationships" }
  }

  override suspend fun createInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
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
              trustedContactAlias = trustedContactAlias
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
    account: Account,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        hwFactorProofOfPossession = hardwareProofOfPossession,
        authTokenScope = authTokenScope
      )
      .catching {
        delete(
          "/api/accounts/${account.accountId.serverId}/recovery/relationships/$relationshipId"
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
    protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
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
              customerEphemeralPublicKey = protectedCustomerEphemeralKey,
              customerIdentityPublicKey = protectedCustomerIdentityKey
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
    code: String,
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
              code = code
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
    sharedSecretCiphertext: XCiphertext,
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
              sharedSecretCiphertext = sharedSecretCiphertext.value
            )
          )
        }
      }.logNetworkFailure {
        "Failed to verify challenge code"
      }
  }

  override suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
  ): Result<Invitation, F8eError<RetrieveTrustedContactInvitationErrorCode>> {
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
      .map { it.invitation.toInvitation(invitationCode) }
      .mapError { it.toF8eError<RetrieveTrustedContactInvitationErrorCode>() }
  }

  override suspend fun acceptInvitation(
    account: Account,
    invitation: Invitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    trustedContactIdentityKey: TrustedContactIdentityKey,
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
              code = invitation.token,
              customerAlias = protectedCustomerAlias.alias,
              trustedContactIdentityKey = trustedContactIdentityKey.publicKey.value
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
    token = token,
    expiresAt = expiresAt
  )

private fun RetrieveTrustedContactInvitation.toInvitation(invitationCode: String) =
  Invitation(
    recoveryRelationshipId = recoveryRelationshipId,
    // The alias isn't needed when retrieving / accepting, so just pass an empty alias
    trustedContactAlias = TrustedContactAlias(""),
    token = invitationCode,
    expiresAt = expiresAt
  )
