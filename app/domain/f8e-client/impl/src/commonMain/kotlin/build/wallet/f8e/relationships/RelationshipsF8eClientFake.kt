package build.wallet.f8e.relationships

import bitkey.auth.AuthTokenScope
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import bitkey.f8e.error.code.F8eClientErrorCode
import bitkey.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import bitkey.relationships.Relationships
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.bitkey.relationships.*
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SealedData
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.HttpError.UnhandledException
import build.wallet.ktor.result.NetworkingError
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
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
 * A functional fake implementation of the Relationships service for testing and local development.
 *
 * @param uuidGenerator - the uuid to use for generating random ids
 * @param backgroundScope - the scope to use for background tasks
 * @param clock - the clock to use getting the current time
 */
@Fake
@BitkeyInject(AppScope::class)
class RelationshipsF8eClientFake(
  private val uuidGenerator: UuidGenerator,
  private val backgroundScope: CoroutineScope,
  private val clock: Clock,
) : RelationshipsF8eClient {
  private val invitations = mutableListOf<InvitationPair>()
  val unendorsedTrustedContacts = mutableListOf<UnendorsedTrustedContact>()
  val keyCertificates = mutableListOf<TrustedContactKeyCertificate>()
  val endorsedTrustedContacts = mutableListOf<EndorsedTrustedContact>()
  val protectedCustomers = mutableListOf<ProtectedCustomer>()
  var fakeNetworkingError: NetworkingError? = null

  var acceptInvitationDelay: Duration = 10.seconds

  private data class InvitationPair(
    val outgoing: Invitation,
    val incoming: IncomingInvitation,
  )

  private fun genServerInviteCode(): String {
    val bytes = Random.nextBytes(3)
    bytes[2] = bytes[2] and 0xF0.toByte()
    return bytes.toByteString().hex()
  }

  override suspend fun createInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
    protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
    roles: Set<TrustedContactRole>,
  ): Result<Invitation, NetworkingError> {
    if (invitations.any { it.outgoing.trustedContactAlias == trustedContactAlias }) {
      return Err(
        UnhandledException(Exception("Invitation for alias $trustedContactAlias already exists."))
      )
    }
    val outgoing = Invitation(
      relationshipId = uuidGenerator.random(),
      trustedContactAlias = trustedContactAlias,
      code = genServerInviteCode(),
      codeBitLength = 20,
      expiresAt = clock.now().plus(7.days),
      roles = roles
    )

    val invitation = InvitationPair(
      outgoing = outgoing,
      incoming = IncomingInvitation(
        relationshipId = outgoing.relationshipId,
        code = outgoing.code,
        protectedCustomerEnrollmentPakeKey = protectedCustomerEnrollmentPakeKey,
        recoveryRelationshipRoles = roles
      )
    )
    invitations += invitation

    backgroundScope.launch {
      // Promote the invitation to a RC after some time:
      delay(acceptInvitationDelay)
      if (invitations.remove(invitation)) {
        unendorsedTrustedContacts.add(
          UnendorsedTrustedContact(
            relationshipId = invitation.outgoing.relationshipId,
            trustedContactAlias = invitation.outgoing.trustedContactAlias,
            sealedDelegatedDecryptionKey = XCiphertext("deadbeef"),
            enrollmentPakeKey = PublicKey("deadbeef"),
            enrollmentKeyConfirmation = "deadbeef".encodeUtf8(),
            authenticationState = TrustedContactAuthenticationState.UNAUTHENTICATED,
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
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
      invitations.find { it.outgoing.relationshipId == relationshipId }
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
  ): Result<Relationships, NetworkingError> {
    return fakeNetworkingError?.let(::Err) ?: Ok(
      Relationships(
        invitations = invitations.map { it.outgoing },
        endorsedTrustedContacts = endorsedTrustedContacts.toList(),
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

    if (invitations.removeAll { it.outgoing.relationshipId == relationshipId } ||
      endorsedTrustedContacts.removeAll { it.relationshipId == relationshipId } ||
      protectedCustomers.removeAll { it.relationshipId == relationshipId }
    ) {
      return Ok(Unit)
    }
    return Err(UnhandledException(Exception("Relationship $relationshipId not found.")))
  }

  override suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
  ): Result<IncomingInvitation, F8eError<RetrieveTrustedContactInvitationErrorCode>> {
    return Ok(
      IncomingInvitation(
        relationshipId = uuidGenerator.random(),
        code = genServerInviteCode(),
        protectedCustomerEnrollmentPakeKey = PublicKey("deadbeef"),
        recoveryRelationshipRoles = setOf()
      )
    )
  }

  override suspend fun acceptInvitation(
    account: Account,
    invitation: IncomingInvitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    trustedContactEnrollmentPakeKey: PublicKey<TrustedContactEnrollmentPakeKey>,
    enrollmentPakeConfirmation: ByteString,
    sealedDelegateDecryptionKeyCipherText: XCiphertext,
  ): Result<ProtectedCustomer, F8eError<AcceptTrustedContactInvitationErrorCode>> {
    val protectedCustomer =
      ProtectedCustomer(
        relationshipId = invitation.relationshipId,
        alias = protectedCustomerAlias,
        roles = setOf(TrustedContactRole.SocialRecoveryContact)
      )
    protectedCustomers.add(protectedCustomer)
    return Ok(protectedCustomer)
  }

  override suspend fun endorseTrustedContacts(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    endorsements: List<TrustedContactEndorsement>,
  ): Result<Unit, Error> {
    endorsements.forEach { (relationshipId, certificate) ->
      // Find known unendorsed Recovery Contacts based on given endorsement
      val unendorsedContact =
        unendorsedTrustedContacts.find { it.relationshipId == relationshipId.value }

      if (unendorsedContact != null) {
        // Add new certificates
        keyCertificates += certificate

        // Promote an unendorsed RC to an endorsed RC
        unendorsedTrustedContacts.remove(unendorsedContact)
        endorsedTrustedContacts.add(
          EndorsedTrustedContact(
            relationshipId = relationshipId.value,
            trustedContactAlias = unendorsedContact.trustedContactAlias,
            authenticationState = TrustedContactAuthenticationState.VERIFIED,
            keyCertificate = certificate,
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          )
        )
      }
    }

    return Ok(Unit)
  }

  override suspend fun uploadSealedDelegatedDecryptionKeyData(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    sealedData: SealedData,
  ): Result<Unit, NetworkingError> {
    return Ok(Unit)
  }

  override suspend fun getSealedDelegatedDecryptionKeyData(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<SealedData, NetworkingError> {
    return Ok(ddkReturnData(accountId, f8eEnvironment))
  }

  override suspend fun retrieveInvitationPromotionCode(
    account: Account,
    invitationCode: String,
  ): Result<PromotionCode?, F8eError<F8eClientErrorCode>> {
    return Ok(PromotionCode("fake-promotion-code"))
  }

  fun deleteInvitation(recoveryRelationshipId: String) {
    invitations.removeAll { it.outgoing.relationshipId == recoveryRelationshipId }
  }

  fun reset() {
    invitations.clear()
    unendorsedTrustedContacts.clear()
    endorsedTrustedContacts.clear()
    protectedCustomers.clear()
    keyCertificates.clear()
    fakeNetworkingError = null
  }

  fun ddkReturnData(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): ByteString {
    return ("deadbeef-" + accountId + "-" + f8eEnvironment).encodeUtf8()
  }
}
