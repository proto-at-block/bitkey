package build.wallet.recovery.socrec

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.bitkey.socrec.IncomingInvitation
import build.wallet.bitkey.socrec.OutgoingInvitation
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.f8e.sync.F8eSyncSequencer
import build.wallet.isOk
import build.wallet.logging.logFailure
import build.wallet.recovery.socrec.InviteCodeParts.Schema
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class SocRecRelationshipsRepositoryImpl(
  private val socialRecoveryServiceProvider: SocialRecoveryServiceProvider,
  private val socRecRelationshipsDao: SocRecRelationshipsDao,
  private val socRecEnrollmentAuthenticationDao: SocRecEnrollmentAuthenticationDao,
  private val socRecCrypto: SocRecCrypto,
  private val socialRecoveryCodeBuilder: SocialRecoveryCodeBuilder,
) : SocRecRelationshipsRepository {
  private val f8eSyncSequencer = F8eSyncSequencer()

  internal suspend fun socRecService() = socialRecoveryServiceProvider.get()

  private val relationshipsState = MutableStateFlow<SocRecRelationships?>(null)

  override val relationships: Flow<SocRecRelationships> = relationshipsState.filterNotNull()

  override suspend fun getRelationshipsWithoutSyncing(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): SocRecRelationships =
    socRecService().getRelationships(
      accountId,
      f8eEnvironment,
      hardwareProofOfPossession = null
    ).getOr(SocRecRelationships.EMPTY)

  override fun syncLoop(
    scope: CoroutineScope,
    account: Account,
  ) {
    // Sync from db
    scope.launch {
      socRecRelationshipsDao.socRecRelationships()
        .map { result ->
          result
            .logFailure { "Failed to get relationships" }
            .getOr(SocRecRelationships.EMPTY)
        }
        .collect(relationshipsState)
    }

    // Sync data from server
    scope.launch {
      f8eSyncSequencer.run(account) {
        while (isActive) {
          syncAndVerifyRelationships(account)
          delay(5.seconds)
        }
      }
    }
  }

  override suspend fun removeRelationshipWithoutSyncing(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, Error> {
    return socRecService()
      .removeRelationship(
        accountId = accountId,
        f8eEnvironment = f8eEnvironment,
        hardwareProofOfPossession = hardwareProofOfPossession,
        authTokenScope = authTokenScope,
        relationshipId = relationshipId
      )
  }

  override suspend fun removeRelationship(
    account: Account,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, Error> {
    return socRecService()
      .removeRelationship(
        accountId = account.accountId,
        f8eEnvironment = account.config.f8eEnvironment,
        hardwareProofOfPossession = hardwareProofOfPossession,
        authTokenScope = authTokenScope,
        relationshipId = relationshipId
      )
      .also {
        syncAndVerifyRelationships(account)
      }
  }

  override suspend fun createInvitation(
    account: FullAccount,
    trustedContactAlias: TrustedContactAlias,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<OutgoingInvitation, Error> =
    binding {
      // TODO: Use SocRecCrypto to generate.
      val enrollmentPakeCode = Schema.maskPakeData(Random.nextBytes(Schema.pakeByteArraySize()))
      val protectedCustomerEnrollmentPakeKey =
        socRecCrypto.generateProtectedCustomerEnrollmentPakeKey(enrollmentPakeCode).bind()
      socRecService()
        .createInvitation(
          account = account,
          hardwareProofOfPossession = hardwareProofOfPossession,
          trustedContactAlias = trustedContactAlias,
          protectedCustomerEnrollmentPakeKey = protectedCustomerEnrollmentPakeKey
        )
        .flatMap { invitation ->
          socRecEnrollmentAuthenticationDao
            .insert(
              invitation.recoveryRelationshipId,
              protectedCustomerEnrollmentPakeKey,
              enrollmentPakeCode
            )
            .mapError { Error("Failed to insert into socRecEnrollmentAuthenticationDao", it) }
            .flatMap {
              socialRecoveryCodeBuilder.buildInviteCode(
                invitation.code,
                invitation.codeBitLength,
                enrollmentPakeCode
              )
            }
            .mapError { Error("Failed to build invite code", it) }
            .map { OutgoingInvitation(invitation, it) }
        }.bind()
        .also { syncAndVerifyRelationships(account) }
    }

  override suspend fun refreshInvitation(
    account: FullAccount,
    relationshipId: String,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<OutgoingInvitation, Error> {
    return socRecService()
      .refreshInvitation(account, hardwareProofOfPossession, relationshipId)
      .flatMap { invitation ->
        socRecEnrollmentAuthenticationDao.getByRelationshipId(relationshipId)
          .mapError { Error("Failed to get PAKE data", it) }
          .toErrorIfNull { Error("Can't refresh invitation if PAKE data isn't present") }
          .map { PakeCode(it.pakeCode) }
          .flatMap {
            socialRecoveryCodeBuilder.buildInviteCode(
              serverPart = invitation.code,
              serverBits = invitation.codeBitLength,
              pakePart = it
            )
          }
          .mapError { Error("Failed to build invite code", it) }
          .map { OutgoingInvitation(invitation, it) }
      }
      .also { syncAndVerifyRelationships(account) }
  }

  override suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
  ): Result<IncomingInvitation, RetrieveInvitationCodeError> {
    return socialRecoveryCodeBuilder.parseInviteCode(invitationCode)
      .mapError {
        when (it) {
          is SocialRecoveryCodeVersionError -> RetrieveInvitationCodeError.InvitationCodeVersionMismatch
          else -> RetrieveInvitationCodeError.InvalidInvitationCode
        }
      }
      .andThen { (serverPart, _) ->
        socRecService().retrieveInvitation(
          account,
          serverPart
        )
          .mapError {
            RetrieveInvitationCodeError.F8ePropagatedError(it)
          }
      }
  }

  override suspend fun acceptInvitation(
    account: Account,
    invitation: IncomingInvitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    delegatedDecryptionKey: DelegatedDecryptionKey,
    inviteCode: String,
  ): Result<ProtectedCustomer, AcceptInvitationCodeError> {
    return socialRecoveryCodeBuilder.parseInviteCode(inviteCode)
      .mapError { AcceptInvitationCodeError.InvalidInvitationCode }
      .andThen { (_, pakePart) ->
        socRecCrypto.encryptDelegatedDecryptionKey(
          pakePart,
          invitation.protectedCustomerEnrollmentPakeKey,
          delegatedDecryptionKey
        )
          .logFailure { "Failed to encrypt trusted contact identity key" }
          .mapError { AcceptInvitationCodeError.CryptoError(it) }
      }
      .andThen {
        socRecService()
          .acceptInvitation(
            account = account,
            invitation = invitation,
            protectedCustomerAlias = protectedCustomerAlias,
            trustedContactEnrollmentPakeKey = it.trustedContactEnrollmentPakeKey,
            enrollmentPakeConfirmation = it.keyConfirmation,
            sealedDelegateDecryptionKeyCipherText = it.sealedDelegatedDecryptionKey
          )
          .mapError { AcceptInvitationCodeError.F8ePropagatedError(it) }
      }
      .also { syncAndVerifyRelationships(account) }
  }

  override suspend fun syncRelationshipsWithoutVerification(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<SocRecRelationships, Error> =
    binding {
      // Fetch latest relationships from f8e
      val relationships = socRecService()
        .getRelationships(accountId, f8eEnvironment, hardwareProofOfPossession = null)
        .bind()

      socRecRelationshipsDao.setSocRecRelationships(relationships)
        .bind()

      relationshipsState.value = relationships

      relationships
    }

  override suspend fun syncAndVerifyRelationships(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    appAuthKey: AppGlobalAuthPublicKey?,
    hwAuthPublicKey: HwAuthPublicKey?,
  ): Result<SocRecRelationships, Error> =
    binding {
      // Fetch latest relationships from f8e
      val relationships = socRecService()
        .getRelationships(accountId, f8eEnvironment, hardwareProofOfPossession)
        .bind()

      // If full account, verify relationships using the account's auth keys
      val verifiedRelationships = when (accountId) {
        is FullAccountId -> relationships.verified(appAuthKey, hwAuthPublicKey)
        else -> relationships
      }

      // Update database
      socRecRelationshipsDao.setSocRecRelationships(verifiedRelationships).bind()

      // Update in-memory state
      relationshipsState.value = verifiedRelationships

      verifiedRelationships
    }

  /**
   * Returns a copy of the given relationships with the trusted contacts verified using [account]'s
   * auth keys.
   */
  private fun SocRecRelationships.verified(
    appAuthKey: AppGlobalAuthPublicKey?,
    hwAuthPublicKey: HwAuthPublicKey?,
  ): SocRecRelationships =
    copy(trustedContacts = trustedContacts.map { it.verified(appAuthKey, hwAuthPublicKey) })

  /**
   * Verifies that the given endorsed TC is authentic by signing the key certificate with
   * [account]'s active app and/or auth key.
   */
  private fun TrustedContact.verified(
    appAuthKey: AppGlobalAuthPublicKey?,
    hwAuthPublicKey: HwAuthPublicKey?,
  ): TrustedContact {
    val isVerified = socRecCrypto
      .verifyKeyCertificate(keyCertificate, hwAuthPublicKey, appAuthKey)
      // DO NOT REMOVE this log line. We alert on it.
      // See BKR-858
      .logFailure { "[socrec_key_certificate_verification_failure] Error verifying TC certificate $this" }
      .isOk()

    val authState = when {
      isVerified -> TrustedContactAuthenticationState.VERIFIED
      else -> TrustedContactAuthenticationState.TAMPERED
    }

    return copy(authenticationState = authState)
  }

  override suspend fun clear(): Result<Unit, Error> = socRecRelationshipsDao.clear()
}
