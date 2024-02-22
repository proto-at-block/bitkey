@file:OptIn(ExperimentalStdlibApi::class)

package build.wallet.f8e.socrec

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.bitkey.socrec.SocialChallengeResponse
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import build.wallet.f8e.socrec.models.ChallengeVerificationResponse
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.HttpError.UnhandledException
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.platform.random.Uuid
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.http.HttpStatusCode
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.random.Random.Default
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * A functional fake implementation of the SocialRecoveryService for testing and local development.
 *
 * @param uuid - the uuid to use for generating random ids
 * @param backgroundScope - the scope to use for background tasks
 * @param clock - the clock to use getting the current time
 * @param isReturnFakeChallengeIfMissing - whether to return a fake social challenge if one is
 *  missing when attempting to retrieve a challenge with verifyChallenge.
 */
class SocialRecoveryServiceFake(
  private val uuid: Uuid,
  private val backgroundScope: CoroutineScope,
  private val clock: Clock = Clock.System,
) : SocialRecoveryService {
  val invitations = mutableListOf<Invitation>()
  val trustedContacts = mutableListOf<TrustedContact>()
  val challengeResponses = mutableListOf<SocialChallengeResponse>()
  val protectedCustomers = mutableListOf<ProtectedCustomer>()
  val challenges = mutableListOf<ChallengeWrapper>()
  var fakeNetworkingError: NetworkingError? = null

  data class ChallengeWrapper(
    var protectedCustomerSideOfChallenge: SocialChallenge,
    val protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    val protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    var recoveryRelationshipId: String? = null,
  )

  @OptIn(ExperimentalStdlibApi::class)
  override suspend fun createInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
  ): Result<Invitation, NetworkingError> {
    if (invitations.any { it.trustedContactAlias == trustedContactAlias }) {
      return Err(
        UnhandledException(Exception("Invitation for alias $trustedContactAlias already exists."))
      )
    }
    val invitation =
      Invitation(
        recoveryRelationshipId = uuid.random(),
        trustedContactAlias = trustedContactAlias,
        token = Default.nextBytes(32).toHexString(),
        expiresAt = clock.now().plus(7.days)
      )
    invitations += invitation

    backgroundScope.launch {
      // Promote the invitation to a TC after some time:
      delay(10.seconds)
      invitations.remove(invitation)
      trustedContacts.add(
        TrustedContact(
          recoveryRelationshipId = invitation.recoveryRelationshipId,
          trustedContactAlias = invitation.trustedContactAlias,
          identityKey = TrustedContactIdentityKey(AppKey.fromPublicKey("TODO"))
        )
      )
    }

    return fakeNetworkingError?.let(::Err) ?: Ok(invitation)
  }

  override suspend fun refreshInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    relationshipId: String,
  ): Result<Invitation, NetworkingError> {
    val invitation =
      invitations.find { it.recoveryRelationshipId == relationshipId }
        ?: return Err(UnhandledException(Exception("Invitation $relationshipId not found.")))
    invitations.remove(invitation)

    val newInvitation =
      invitation.copy(
        expiresAt = clock.now().plus(7.days)
      )
    invitations += newInvitation
    return fakeNetworkingError?.let(::Err) ?: Ok(newInvitation)
  }

  override suspend fun getRelationships(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
  ): Result<SocRecRelationships, NetworkingError> {
    return fakeNetworkingError?.let(::Err) ?: Ok(
      SocRecRelationships(
        invitations = invitations,
        trustedContacts = trustedContacts,
        protectedCustomers = protectedCustomers.toImmutableList()
      )
    )
  }

  override suspend fun removeRelationship(
    account: Account,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, NetworkingError> {
    fakeNetworkingError?.let { return Err(it) }

    if (invitations.removeAll { it.recoveryRelationshipId == relationshipId } ||
      trustedContacts.removeAll { it.recoveryRelationshipId == relationshipId } ||
      protectedCustomers.removeAll { it.recoveryRelationshipId == relationshipId }
    ) {
      return Ok(Unit)
    }
    return Err(UnhandledException(Exception("Relationship $relationshipId not found.")))
  }

  @OptIn(ExperimentalStdlibApi::class)
  override suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
  ): Result<Invitation, F8eError<RetrieveTrustedContactInvitationErrorCode>> {
    return Ok(
      Invitation(
        recoveryRelationshipId = uuid.random(),
        trustedContactAlias = TrustedContactAlias("Fake Trusted Contact Alis"),
        token = Default.nextBytes(32).toHexString(),
        expiresAt = Instant.DISTANT_FUTURE
      )
    )
  }

  override suspend fun acceptInvitation(
    account: Account,
    invitation: Invitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    trustedContactIdentityKey: TrustedContactIdentityKey,
  ): Result<ProtectedCustomer, F8eError<AcceptTrustedContactInvitationErrorCode>> {
    val protectedCustomer =
      ProtectedCustomer(
        recoveryRelationshipId = invitation.recoveryRelationshipId,
        alias = protectedCustomerAlias
      )
    protectedCustomers.add(protectedCustomer)
    return Ok(protectedCustomer)
  }

  override suspend fun startChallenge(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
  ): Result<SocialChallenge, NetworkingError> {
    val code = "${Random.nextInt(1000, 9999)}-${Random.nextInt(1000, 9999)}"
    val challengeId = uuid.random() + code
    val challenge =
      SocialChallenge(
        challengeId = challengeId,
        code = code,
        responses = challengeResponses
      )
    challenges.add(
      ChallengeWrapper(
        protectedCustomerSideOfChallenge = challenge,
        protectedCustomerEphemeralKey = protectedCustomerEphemeralKey,
        protectedCustomerIdentityKey = protectedCustomerIdentityKey
      )
    )
    return fakeNetworkingError?.let(::Err) ?: Ok(challenge)
  }

  override suspend fun verifyChallenge(
    account: Account,
    recoveryRelationshipId: String,
    code: String,
  ): Result<ChallengeVerificationResponse, NetworkingError> {
    val challenge = challenges.find { it.protectedCustomerSideOfChallenge.code == code }
    if (challenge == null) {
      return Err(
        HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized))
      )
    }
    return Ok(
      ChallengeVerificationResponse(
        socialChallengeId = challenge.protectedCustomerSideOfChallenge.challengeId,
        customerEphemeralPublicKey = challenge.protectedCustomerEphemeralKey.publicKey.value,
        customerIdentityPublicKey = challenge.protectedCustomerIdentityKey.publicKey.value
      )
    )
  }

  override suspend fun respondToChallenge(
    account: Account,
    socialChallengeId: String,
    sharedSecretCiphertext: XCiphertext,
  ): Result<Unit, NetworkingError> {
    val challenge =
      challenges
        .find { it.protectedCustomerSideOfChallenge.challengeId == socialChallengeId }
    if (challenge == null) {
      return Err(
        HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized))
      )
    }
    challenge.protectedCustomerSideOfChallenge =
      challenge.protectedCustomerSideOfChallenge.copy(
        responses =
          challenge.protectedCustomerSideOfChallenge.responses +
            SocialChallengeResponse(
              recoveryRelationshipId = challenge.recoveryRelationshipId!!,
              sharedSecretCiphertext = sharedSecretCiphertext
            )
      )
    return Ok(Unit)
  }

  override suspend fun getSocialChallengeStatus(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    challengeId: String,
  ): Result<SocialChallenge, NetworkingError> {
    return fakeNetworkingError?.let(::Err) ?: run {
      val challenge =
        challenges
          .find { it.protectedCustomerSideOfChallenge.challengeId == challengeId }
      challenge
        ?.let { Ok(it.protectedCustomerSideOfChallenge) }
        ?: Err(HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized)))
    }
  }

  fun reset() {
    invitations.clear()
    trustedContacts.clear()
    protectedCustomers.clear()
    challenges.clear()
    fakeNetworkingError = null
  }
}
