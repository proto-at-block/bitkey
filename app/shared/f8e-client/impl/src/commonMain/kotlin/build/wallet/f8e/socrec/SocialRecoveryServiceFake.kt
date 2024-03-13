@file:OptIn(ExperimentalStdlibApi::class)

package build.wallet.f8e.socrec

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.IncomingInvitation
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentPakeKey
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.bitkey.socrec.SocialChallengeResponse
import build.wallet.bitkey.socrec.StartSocialChallengeRequestTrustedContact
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState
import build.wallet.bitkey.socrec.TrustedContactEndorsement
import build.wallet.bitkey.socrec.TrustedContactEnrollmentPakeKey
import build.wallet.bitkey.socrec.TrustedContactKeyCertificate
import build.wallet.bitkey.socrec.UnendorsedTrustedContact
import build.wallet.crypto.PublicKey
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
import build.wallet.recovery.socrec.SocRecCryptoFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import io.ktor.http.HttpStatusCode
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.experimental.and
import kotlin.random.Random
import kotlin.time.Duration
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
 * @param acceptInvitationDelay - the delay to use before automatically promoting an invitation to
 * an unendorsed trusted contact.
 */
class SocialRecoveryServiceFake(
  private val uuid: Uuid,
  private val backgroundScope: CoroutineScope,
  private val clock: Clock = Clock.System,
) : SocialRecoveryService {
  private val socRecCrypto = SocRecCryptoFake()
  private val invitations = mutableListOf<InvitationPair>()
  val unendorsedTrustedContacts = mutableListOf<UnendorsedTrustedContact>()
  val keyCertificates = mutableListOf<TrustedContactKeyCertificate>()
  val trustedContacts = mutableListOf<TrustedContact>()
  val challengeResponses = mutableListOf<SocialChallengeResponse>()
  val protectedCustomers = mutableListOf<ProtectedCustomer>()
  val challenges = mutableListOf<FakeServerChallenge>()
  var fakeNetworkingError: NetworkingError? = null

  var acceptInvitationDelay: Duration = 10.seconds

  data class FakeServerChallenge(
    var response: SocialChallenge,
    var recoveryRelationshipId: String? = null,
    val protectedCustomerRecoveryPakePubkey: PublicKey,
    val sealedDek: String,
  )

  private data class InvitationPair(
    val outgoing: Invitation,
    val incoming: IncomingInvitation,
  )

  private fun genServerInviteCode(): String {
    val bytes = Random.nextBytes(3)
    bytes[2] = bytes[2] and 0xF0.toByte()
    return bytes.toByteString().hex()
  }

  @OptIn(ExperimentalStdlibApi::class)
  override suspend fun createInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
    protectedCustomerEnrollmentPakeKey: ProtectedCustomerEnrollmentPakeKey,
  ): Result<Invitation, NetworkingError> {
    if (invitations.any { it.outgoing.trustedContactAlias == trustedContactAlias }) {
      return Err(
        UnhandledException(Exception("Invitation for alias $trustedContactAlias already exists."))
      )
    }
    val outgoing = Invitation(
      recoveryRelationshipId = uuid.random(),
      trustedContactAlias = trustedContactAlias,
      code = genServerInviteCode(),
      codeBitLength = 20,
      expiresAt = clock.now().plus(7.days)
    )

    val invitation = InvitationPair(
      outgoing = outgoing,
      incoming = IncomingInvitation(
        recoveryRelationshipId = outgoing.recoveryRelationshipId,
        code = outgoing.code,
        protectedCustomerEnrollmentPakeKey =
          protectedCustomerEnrollmentPakeKey.copy(
            key = AppKey.fromPublicKey(
              protectedCustomerEnrollmentPakeKey.publicKey.value
            )
          )
      )
    )
    invitations += invitation

    backgroundScope.launch {
      // Promote the invitation to a TC after some time:
      delay(acceptInvitationDelay)
      if (invitations.remove(invitation)) {
        unendorsedTrustedContacts.add(
          UnendorsedTrustedContact(
            recoveryRelationshipId = invitation.outgoing.recoveryRelationshipId,
            trustedContactAlias = invitation.outgoing.trustedContactAlias,
            sealedDelegatedDecryptionKey = XCiphertext("deadbeef"),
            enrollmentPakeKey = TrustedContactEnrollmentPakeKey(AppKey.Companion.fromPublicKey("deadbeef")),
            enrollmentKeyConfirmation = "deadbeef".encodeUtf8(),
            authenticationState = TrustedContactAuthenticationState.UNAUTHENTICATED
          )
        )
      }
    }

    return fakeNetworkingError?.let(::Err) ?: Ok(invitation.outgoing)
  }

  override suspend fun refreshInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    relationshipId: String,
  ): Result<Invitation, NetworkingError> {
    val invitation =
      invitations.find { it.outgoing.recoveryRelationshipId == relationshipId }
        ?: return Err(UnhandledException(Exception("Invitation $relationshipId not found.")))
    invitations.remove(invitation)

    val newInvitation =
      invitation.copy(
        outgoing = invitation.outgoing.copy(
          expiresAt = clock.now().plus(7.days)
        )
      )
    invitations += newInvitation
    return fakeNetworkingError?.let(::Err) ?: Ok(newInvitation.outgoing)
  }

  override suspend fun getRelationships(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
  ): Result<SocRecRelationships, NetworkingError> {
    return fakeNetworkingError?.let(::Err) ?: Ok(
      SocRecRelationships(
        invitations = invitations.map { it.outgoing },
        trustedContacts = trustedContacts.toList(),
        unendorsedTrustedContacts = unendorsedTrustedContacts.toList(),
        protectedCustomers = protectedCustomers.toImmutableList()
      )
    )
  }

  override suspend fun removeRelationship(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, NetworkingError> {
    fakeNetworkingError?.let { return Err(it) }

    if (invitations.removeAll { it.outgoing.recoveryRelationshipId == relationshipId } ||
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
  ): Result<IncomingInvitation, F8eError<RetrieveTrustedContactInvitationErrorCode>> {
    return Ok(
      IncomingInvitation(
        recoveryRelationshipId = uuid.random(),
        code = genServerInviteCode(),
        protectedCustomerEnrollmentPakeKey = ProtectedCustomerEnrollmentPakeKey(
          AppKey.fromPublicKey("deadbeef")
        )
      )
    )
  }

  override suspend fun acceptInvitation(
    account: Account,
    invitation: IncomingInvitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    trustedContactEnrollmentPakeKey: TrustedContactEnrollmentPakeKey,
    enrollmentPakeConfirmation: ByteString,
    sealedDelegateDecryptionKeyCipherText: XCiphertext,
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
    trustedContacts: List<StartSocialChallengeRequestTrustedContact>,
  ): Result<SocialChallenge, NetworkingError> {
    val code = Random.nextInt(0, 100)
    val challengeId = uuid.random() + code
    val challenge =
      SocialChallenge(
        challengeId = challengeId,
        counter = code,
        responses = challengeResponses
      )

    val pakeCode = PakeCode("12345678901".encodeUtf8())
    challenges.add(
      FakeServerChallenge(
        response = challenge,
        protectedCustomerRecoveryPakePubkey = socRecCrypto.generateProtectedCustomerRecoveryPakeKey(pakeCode)
          .getOrThrow().publicKey,
        sealedDek = "sealed-dek"
      )
    )
    return fakeNetworkingError?.let(::Err) ?: Ok(challenge)
  }

  override suspend fun verifyChallenge(
    account: Account,
    recoveryRelationshipId: String,
    counter: Int,
  ): Result<ChallengeVerificationResponse, NetworkingError> {
    val challenge = challenges.find { it.response.counter == counter }
    if (challenge == null) {
      return Err(
        HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized))
      )
    }

    return Ok(
      ChallengeVerificationResponse(
        socialChallengeId = challenge.response.challengeId,
        protectedCustomerRecoveryPakePubkey = challenge.protectedCustomerRecoveryPakePubkey,
        sealedDek = challenge.sealedDek
      )
    )
  }

  override suspend fun respondToChallenge(
    account: Account,
    socialChallengeId: String,
    trustedContactRecoveryPakePubkey: PublicKey,
    recoveryPakeConfirmation: ByteString,
    resealedDek: XCiphertext,
  ): Result<Unit, NetworkingError> {
    val challenge =
      challenges
        .find { it.response.challengeId == socialChallengeId }
    if (challenge == null) {
      return Err(
        HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized))
      )
    }
    challenge.response =
      challenge.response.copy(
        responses =
          challenge.response.responses +
            SocialChallengeResponse(
              recoveryRelationshipId = challenge.recoveryRelationshipId!!,
              trustedContactRecoveryPakePubkey = trustedContactRecoveryPakePubkey,
              recoveryPakeConfirmation = recoveryPakeConfirmation,
              resealedDek = resealedDek
            )
      )
    return Ok(Unit)
  }

  override suspend fun endorseTrustedContacts(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    endorsements: List<TrustedContactEndorsement>,
  ): Result<Unit, Error> {
    endorsements.forEach { (relationshipId, certificate) ->
      // Find known unendorsed trusted contacts based on given endorsement
      val unendorsedContact =
        unendorsedTrustedContacts.find { it.recoveryRelationshipId == relationshipId.value }

      if (unendorsedContact != null) {
        // Add new certificates
        keyCertificates += certificate

        // Promote an unendorsed TC to an endorsed TC
        unendorsedTrustedContacts.remove(unendorsedContact)
        trustedContacts.add(
          TrustedContact(
            recoveryRelationshipId = relationshipId.value,
            trustedContactAlias = unendorsedContact.trustedContactAlias,
            authenticationState = TrustedContactAuthenticationState.VERIFIED,
            keyCertificate = certificate
          )
        )
      }
    }

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
          .find { it.response.challengeId == challengeId }
      challenge
        ?.let { Ok(it.response) }
        ?: Err(HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized)))
    }
  }

  fun deleteInvitation(recoveryRelationshipId: String) {
    invitations.removeAll { it.outgoing.recoveryRelationshipId == recoveryRelationshipId }
  }

  fun reset() {
    invitations.clear()
    unendorsedTrustedContacts.clear()
    trustedContacts.clear()
    protectedCustomers.clear()
    challenges.clear()
    keyCertificates.clear()
    fakeNetworkingError = null
  }
}
