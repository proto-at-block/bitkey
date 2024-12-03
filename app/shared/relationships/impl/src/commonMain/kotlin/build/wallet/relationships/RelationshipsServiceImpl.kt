package build.wallet.relationships

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.relationships.*
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.relationships.Relationships
import build.wallet.f8e.sync.F8eSyncSequencer
import build.wallet.isOk
import build.wallet.logging.logFailure
import build.wallet.mapResult
import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class RelationshipsServiceImpl(
  private val relationshipsF8eClientProvider: RelationshipsF8eClientProvider,
  private val relationshipsDao: RelationshipsDao,
  private val relationshipsEnrollmentAuthenticationDao: RelationshipsEnrollmentAuthenticationDao,
  private val relationshipsCrypto: RelationshipsCrypto,
  private val relationshipsCodeBuilder: RelationshipsCodeBuilder,
  private val appSessionManager: AppSessionManager,
  private val accountService: AccountService,
  appCoroutineScope: CoroutineScope,
) : RelationshipsService, SyncRelationshipsWorker {
  private val f8eSyncSequencer = F8eSyncSequencer()

  private suspend fun relationshipsF8eClient() = relationshipsF8eClientProvider.get()

  // Sync relationships from the database into memory cache
  override val relationships: StateFlow<Relationships?> = relationshipsDao.relationships()
    .map { result ->
      result
        .logFailure { "Failed to get relationships" }
        .getOr(Relationships.EMPTY)
    }
    .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

  override suspend fun getRelationshipsWithoutSyncing(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Relationships, Error> =
    relationshipsF8eClient().getRelationships(
      accountId,
      f8eEnvironment
    )

  override suspend fun executeWork() {
    coroutineScope {
      // Sync relationships with f8e
      launch {
        accountService.accountStatus()
          .mapResult { it as? AccountStatus.ActiveAccount }
          .mapNotNull { it.get()?.account }
          .distinctUntilChanged()
          .collectLatest { account ->
            f8eSyncSequencer.run(account) {
              while (isActive) {
                if (appSessionManager.isAppForegrounded()) {
                  syncAndVerifyRelationships(account)
                }
                delay(5.seconds)
              }
            }
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
    return relationshipsF8eClient()
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
    return relationshipsF8eClient()
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
    roles: Set<TrustedContactRole>,
  ): Result<OutgoingInvitation, Error> =
    coroutineBinding {
      // TODO: Use RelationshipsCrypto to generate.
      val enrollmentPakeCode =
        InviteCodeParts.Schema.maskPakeData(Random.nextBytes(InviteCodeParts.Schema.pakeByteArraySize()))
      val protectedCustomerEnrollmentPakeKey =
        relationshipsCrypto.generateProtectedCustomerEnrollmentPakeKey(enrollmentPakeCode)
          .mapError { Error("Error creating pake key: ${it.message}", it) }
          .bind()
      relationshipsF8eClient()
        .createInvitation(
          account = account,
          hardwareProofOfPossession = hardwareProofOfPossession,
          trustedContactAlias = trustedContactAlias,
          protectedCustomerEnrollmentPakeKey = protectedCustomerEnrollmentPakeKey.publicKey,
          roles = roles
        )
        .flatMap { invitation ->
          relationshipsEnrollmentAuthenticationDao
            .insert(
              invitation.relationshipId,
              protectedCustomerEnrollmentPakeKey,
              enrollmentPakeCode
            )
            .mapError {
              Error("Failed to insert into relationshipsEnrollmentAuthenticationDao", it)
            }
            .flatMap {
              relationshipsCodeBuilder.buildInviteCode(
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
    return relationshipsF8eClient()
      .refreshInvitation(account, hardwareProofOfPossession, relationshipId)
      .flatMap { invitation ->
        relationshipsEnrollmentAuthenticationDao.getByRelationshipId(relationshipId)
          .mapError { Error("Failed to get PAKE data", it) }
          .toErrorIfNull { Error("Can't refresh invitation if PAKE data isn't present") }
          .map { it.pakeCode }
          .flatMap {
            relationshipsCodeBuilder.buildInviteCode(
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
    return relationshipsCodeBuilder.parseInviteCode(invitationCode)
      .mapError { error ->
        when (error) {
          is RelationshipsCodeVersionError -> RetrieveInvitationCodeError.InvitationCodeVersionMismatch(
            error
          )

          else -> RetrieveInvitationCodeError.InvalidInvitationCode(error)
        }
      }
      .andThen { (serverPart, _) ->
        relationshipsF8eClient().retrieveInvitation(
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
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
    inviteCode: String,
  ): Result<ProtectedCustomer, AcceptInvitationCodeError> {
    return relationshipsCodeBuilder.parseInviteCode(inviteCode)
      .mapError { AcceptInvitationCodeError.InvalidInvitationCode }
      .andThen { (_, pakePart) ->
        relationshipsCrypto.encryptDelegatedDecryptionKey(
          pakePart,
          invitation.protectedCustomerEnrollmentPakeKey,
          delegatedDecryptionKey
        )
          .logFailure { "Failed to encrypt trusted contact identity key" }
          .mapError { AcceptInvitationCodeError.CryptoError(it) }
      }
      .andThen {
        relationshipsF8eClient()
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

  override suspend fun syncAndVerifyRelationships(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    appAuthKey: PublicKey<AppGlobalAuthKey>?,
    hwAuthPublicKey: HwAuthPublicKey?,
  ): Result<Relationships, Error> =
    coroutineBinding {
      // Fetch latest relationships from f8e
      val relationships = relationshipsF8eClient()
        .getRelationships(accountId, f8eEnvironment)
        .bind()

      // If full account, verify relationships using the account's auth keys
      val verifiedRelationships = when (accountId) {
        is FullAccountId -> relationships.verified(appAuthKey, hwAuthPublicKey)
        else -> relationships
      }

      // Update database
      relationshipsDao.setRelationships(verifiedRelationships).bind()

      verifiedRelationships
    }

  /**
   * Returns a copy of the given relationships with the trusted contacts verified using [account]'s
   * auth keys.
   */
  private fun Relationships.verified(
    appAuthKey: PublicKey<AppGlobalAuthKey>?,
    hwAuthPublicKey: HwAuthPublicKey?,
  ): Relationships =
    copy(
      endorsedTrustedContacts = endorsedTrustedContacts.map {
        it.verified(appAuthKey, hwAuthPublicKey)
      }
    )

  /**
   * Verifies that the given endorsed TC is authentic by signing the key certificate with
   * [account]'s active app and/or auth key.
   */
  private fun EndorsedTrustedContact.verified(
    appAuthKey: PublicKey<AppGlobalAuthKey>?,
    hwAuthPublicKey: HwAuthPublicKey?,
  ): EndorsedTrustedContact {
    val isVerified = relationshipsCrypto
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

  override suspend fun clear(): Result<Unit, Error> = relationshipsDao.clear()
}
