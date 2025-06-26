package build.wallet.relationships

import bitkey.account.AccountConfigService
import bitkey.auth.AuthTokenScope
import bitkey.relationships.Relationships
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.bitkey.relationships.*
import build.wallet.coroutines.flow.tickerFlow
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.sync.F8eSyncSequencer
import build.wallet.isOk
import build.wallet.logging.logFailure
import build.wallet.mapResult
import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Clock
import kotlin.random.Random

@BitkeyInject(AppScope::class)
class RelationshipsServiceImpl(
  private val relationshipsF8eClientProvider: RelationshipsF8eClientProvider,
  private val relationshipsDao: RelationshipsDao,
  private val relationshipsEnrollmentAuthenticationDao: RelationshipsEnrollmentAuthenticationDao,
  private val relationshipsCrypto: RelationshipsCrypto,
  private val relationshipsCodeBuilder: RelationshipsCodeBuilder,
  private val appSessionManager: AppSessionManager,
  private val accountService: AccountService,
  private val accountConfigService: AccountConfigService,
  private val clock: Clock,
  appCoroutineScope: CoroutineScope,
  private val relationshipsSyncFrequency: RelationshipsSyncFrequency,
) : RelationshipsService, SyncRelationshipsWorker {
  private val f8eSyncSequencer = F8eSyncSequencer()

  private fun relationshipsF8eClient() = relationshipsF8eClientProvider.get()

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
  ): Result<Relationships, Error> {
    return relationshipsF8eClient().getRelationships(
      accountId,
      accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
    )
  }

  override suspend fun executeWork() {
    // Sync relationships with f8e
    accountService.accountStatus()
      .mapResult { it as? AccountStatus.ActiveAccount }
      .map { it.get()?.account }
      .distinctUntilChanged()
      .flatMapLatest { account ->
        account?.let {
          tickerFlow(relationshipsSyncFrequency.value)
            .map { account }
            .filter { appSessionManager.isAppForegrounded() }
        } ?: emptyFlow()
      }
      .collectLatest { account ->
        f8eSyncSequencer.run(account) {
          syncAndVerifyRelationships(account)
        }
      }
  }

  override suspend fun removeRelationshipWithoutSyncing(
    accountId: AccountId,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, Error> {
    return relationshipsF8eClient()
      .removeRelationship(
        accountId = accountId,
        f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment,
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
              Error(
                "Failed to insert into relationshipsEnrollmentAuthenticationDao",
                it
              )
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
      .andThen { invitation ->
        if (invitation.expiresAt < clock.now()) {
          Err(
            RetrieveInvitationCodeError.ExpiredInvitationCode(
              cause = Error("Invitation expired at ${invitation.expiresAt}")
            )
          )
        } else {
          Ok(invitation)
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
          .logFailure { "Failed to encrypt Recovery Contact identity key" }
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

  override suspend fun retrieveInvitationPromotionCode(
    account: Account,
    invitationCode: String,
  ): Result<PromotionCode?, RetrieveInvitationPromotionCodeError> {
    return relationshipsCodeBuilder.parseInviteCode(invitationCode)
      .mapError { RetrieveInvitationPromotionCodeError.InvalidInvitationCode(it) }
      .andThen { (serverPart, _) ->
        relationshipsF8eClient().retrieveInvitationPromotionCode(
          account,
          serverPart
        )
          .mapError { RetrieveInvitationPromotionCodeError.F8ePropagatedError(it) }
      }
  }

  override suspend fun syncAndVerifyRelationships(
    accountId: AccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>?,
    hwAuthPublicKey: HwAuthPublicKey?,
  ): Result<Relationships, Error> =
    coroutineBinding {
      // Fetch latest relationships from f8e
      val relationships = relationshipsF8eClient()
        .getRelationships(
          accountId = accountId,
          f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
        )
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
   * Returns a copy of the given relationships with the Recovery Contacts verified using [account]'s
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
   * Verifies that the given endorsed RC is authentic by signing the key certificate with
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
      .logFailure { "[socrec_key_certificate_verification_failure] Error verifying RC certificate $this" }
      .isOk()

    val authState = when {
      isVerified -> TrustedContactAuthenticationState.VERIFIED
      else -> TrustedContactAuthenticationState.TAMPERED
    }

    return copy(authenticationState = authState)
  }

  override suspend fun clear(): Result<Unit, Error> = relationshipsDao.clear()
}
