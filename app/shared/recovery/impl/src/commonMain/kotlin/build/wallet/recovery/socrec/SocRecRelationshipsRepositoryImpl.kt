package build.wallet.recovery.socrec

import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus
import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.f8e.socrec.SocialRecoveryService
import build.wallet.f8e.sync.F8eSyncSequencer
import build.wallet.keybox.config.TemplateKeyboxConfigDao
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class SocRecRelationshipsRepositoryImpl(
  private val accountRepository: AccountRepository,
  private val socRecRelationshipsDao: SocRecRelationshipsDao,
  private val socRecFake: SocialRecoveryService,
  private val socRecService: SocialRecoveryService,
  scope: CoroutineScope,
  private val templateKeyboxConfigDao: TemplateKeyboxConfigDao,
) : SocRecRelationshipsRepository {
  private val f8eSyncSequencer = F8eSyncSequencer()

  private suspend fun isUsingSocRecFakes(): Boolean {
    return accountRepository
      .accountStatus()
      .first()
      .map { status ->
        when (status) {
          is AccountStatus.ActiveAccount ->
            status.account.config.isUsingSocRecFakes
          is AccountStatus.OnboardingAccount ->
            status.account.config.isUsingSocRecFakes
          is AccountStatus.LiteAccountUpgradingToFullAccount ->
            status.account.config.isUsingSocRecFakes

          is AccountStatus.NoAccount -> {
            templateKeyboxConfigDao.config().first().get()?.isUsingSocRecFakes ?: false
          }
        }
      }.component1() ?: false
  }

  internal suspend fun socRecService(): SocialRecoveryService {
    return if (isUsingSocRecFakes()) {
      socRecFake
    } else {
      socRecService
    }
  }

  override val relationships =
    socRecRelationshipsDao.socRecRelationships().map { result ->
      result
        .logFailure { "Failed to get relationships" }
        .getOr(SocRecRelationships.EMPTY)
    }.stateIn(scope, SharingStarted.Lazily, SocRecRelationships.EMPTY)

  override suspend fun syncLoop(account: Account) {
    // Kick off the StateFlow subscription
    coroutineScope {
      launch {
        relationships.first()
      }
    }
    // Sync data from server
    f8eSyncSequencer.run(account.accountId) {
      while (true) {
        syncRelationships(account)
        delay(5.seconds)
      }
    }
  }

  override suspend fun removeRelationship(
    account: Account,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, Error> {
    return socRecService()
      .removeRelationship(account, hardwareProofOfPossession, authTokenScope, relationshipId)
      .also { syncRelationships(account) }
  }

  override suspend fun createInvitation(
    account: FullAccount,
    trustedContactAlias: TrustedContactAlias,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Invitation, Error> {
    return socRecService()
      .createInvitation(account, hardwareProofOfPossession, trustedContactAlias)
      .also { syncRelationships(account) }
  }

  override suspend fun refreshInvitation(
    account: FullAccount,
    relationshipId: String,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Invitation, Error> {
    return socRecService()
      .refreshInvitation(account, hardwareProofOfPossession, relationshipId)
      .also { syncRelationships(account) }
  }

  override suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
  ): Result<Invitation, F8eError<RetrieveTrustedContactInvitationErrorCode>> {
    return socRecService().retrieveInvitation(
      account,
      invitationCode
    )
  }

  override suspend fun acceptInvitation(
    account: Account,
    invitation: Invitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    trustedContactIdentityKey: TrustedContactIdentityKey,
  ): Result<ProtectedCustomer, F8eError<AcceptTrustedContactInvitationErrorCode>> {
    return socRecService()
      .acceptInvitation(
        account = account,
        invitation = invitation,
        protectedCustomerAlias = protectedCustomerAlias,
        trustedContactIdentityKey = trustedContactIdentityKey
      )
      .also { syncRelationships(account.accountId, account.config.f8eEnvironment) }
  }

  /**
   * Pulls the latest relationships from f8e and updates the local database.
   */
  override suspend fun syncRelationships(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<SocRecRelationships, Error> {
    return socRecService()
      .getRelationships(accountId, f8eEnvironment, hardwareProofOfPossession = null)
      .onSuccess {
        socRecRelationshipsDao.setSocRecRelationships(it)
      }
  }

  private suspend fun syncRelationships(account: Account) =
    syncRelationships(
      account.accountId,
      account.config.f8eEnvironment
    )

  override suspend fun clear(): Result<Unit, Error> = socRecRelationshipsDao.clear()
}
