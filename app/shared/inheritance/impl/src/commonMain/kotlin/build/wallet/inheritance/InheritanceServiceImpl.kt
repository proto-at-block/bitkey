package build.wallet.inheritance

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.analytics.events.AppSessionManager
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaims
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.ensure
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.inheritance.RetrieveInheritanceClaimsF8eClient
import build.wallet.f8e.inheritance.StartInheritanceClaimF8eClient
import build.wallet.f8e.inheritance.UploadInheritanceMaterialF8eClient
import build.wallet.f8e.relationships.Relationships
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.mapResult
import build.wallet.relationships.RelationshipsService
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.SharingStarted.Companion.Lazily
import kotlin.time.Duration.Companion.seconds

class InheritanceServiceImpl(
  private val accountService: AccountService,
  private val relationshipsService: RelationshipsService,
  appCoroutineScope: CoroutineScope,
  private val inheritanceSyncDao: InheritanceSyncDao,
  private val inheritanceMaterialF8eClient: UploadInheritanceMaterialF8eClient,
  private val startInheritanceClaimF8eClient: StartInheritanceClaimF8eClient,
  private val retrieveInheritanceClaimsF8EClient: RetrieveInheritanceClaimsF8eClient,
  private val inheritanceMaterialCreator: InheritanceMaterialCreator,
  private val inheritanceClaimsDao: InheritanceClaimsDao,
  private val appSessionManager: AppSessionManager,
  private val inheritanceFeatureFlag: InheritanceFeatureFlag,
) : InheritanceService, InheritanceClaimsSyncWorker {
  private val syncDelay = 60.seconds

  override val pendingClaims: StateFlow<Result<List<RelationshipId>, Error>?> = combine(
    inheritanceClaimsDao.pendingBenefactorClaims.mapResult { it.map { it.relationshipId } },
    inheritanceClaimsDao.pendingBeneficiaryClaims.mapResult { it.map { it.relationshipId } }
  ) { beneficiaryIds, benefactorIds ->
    coroutineBinding {
      beneficiaryIds.bind() + benefactorIds.bind()
    }
  }
    .stateIn(appCoroutineScope, Lazily, null)

  override suspend fun executeWork() {
    combine(
      inheritanceFeatureFlag.flagValue(),
      accountService.accountStatus()
        .mapResult { it as? AccountStatus.ActiveAccount }
        .mapNotNull { it.get()?.account as? FullAccount }
        .distinctUntilChanged()
    ) { flag, account ->
      flag to account
    }.collectLatest { (flag, account) ->
      while (currentCoroutineContext().isActive && flag.isEnabled()) {
        if (appSessionManager.isAppForegrounded()) {
          syncInheritanceClaims(account)
            .logFailure { "Failed to sync inheritance claims" }
        }
        delay(syncDelay)
      }
    }
  }

  override val inheritanceRelationships: StateFlow<Relationships?> =
    relationshipsService.relationships
      .filterNotNull()
      .map { relationships ->
        Relationships(
          invitations = relationships.invitations
            .filter { it.roles.contains(TrustedContactRole.Beneficiary) },
          endorsedTrustedContacts = relationships.endorsedTrustedContacts
            .filter { it.roles.contains(TrustedContactRole.Beneficiary) },
          unendorsedTrustedContacts = relationships.unendorsedTrustedContacts
            .filter { it.roles.contains(TrustedContactRole.Beneficiary) },
          protectedCustomers = relationships.protectedCustomers
            .filter { it.roles.contains(TrustedContactRole.Beneficiary) }
            .toImmutableList()
        )
      }
      .stateIn(appCoroutineScope, Eagerly, null)

  override suspend fun createInheritanceInvitation(
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
  ): Result<OutgoingInvitation, Error> =
    coroutineBinding {
      val account = accountService.activeAccount().first()
      ensure(account is FullAccount) { Error("No active full account present.") }

      relationshipsService.createInvitation(
        account = account,
        trustedContactAlias = trustedContactAlias,
        hardwareProofOfPossession = hardwareProofOfPossession,
        roles = setOf(TrustedContactRole.Beneficiary)
      ).bind()
    }

  override suspend fun syncInheritanceMaterial(keybox: Keybox): Result<Unit, Error> =
    coroutineBinding {
      val lastSyncHash = inheritanceSyncDao.getSyncedInheritanceMaterialHash().bind()
      val currentMaterialHashData =
        inheritanceMaterialCreator.getInheritanceMaterialHashData(keybox).bind()

      if (lastSyncHash == currentMaterialHashData.inheritanceMaterialHash) {
        log(LogLevel.Debug) { "Inheritance Material is up-to-date. Skipping inheritance material sync" }
        return@coroutineBinding
      }

      if (currentMaterialHashData.contacts.isEmpty() && lastSyncHash == null) {
        log(LogLevel.Debug) {
          "No inheritance contacts to sync initial data. Skipping inheritance material sync"
        }
        return@coroutineBinding
      }

      val account = accountService.activeAccount().first()
      if (account !is FullAccount) {
        log(LogLevel.Debug) { "No full-account found. Skipping inheritance material sync" }
        return@coroutineBinding
      }

      val inheritanceMaterial = inheritanceMaterialCreator.createInheritanceMaterial(keybox).bind()

      inheritanceMaterialF8eClient.uploadInheritanceMaterial(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId,
        inheritanceMaterial = inheritanceMaterial
      ).mapError { Error("Failed inheritance material server-sync", it) }
        .onSuccess {
          log(LogLevel.Debug) { "Inheritance Material Sync Successful" }
        }
        .bind()

      inheritanceSyncDao.updateSyncedInheritanceMaterialHash(
        hash = currentMaterialHashData.inheritanceMaterialHash
      ).bind()
    }

  override suspend fun startInheritanceClaim(
    relationshipId: RelationshipId,
  ): Result<BeneficiaryClaim.PendingClaim, Throwable> {
    val account = accountService.activeAccount().first()
    if (account !is FullAccount) {
      log(LogLevel.Error) { "Start claim cannot be called without full account." }
      return Err(Error("Not a full account"))
    }

    return startInheritanceClaimF8eClient.startInheritanceClaim(
      fullAccount = account,
      relationshipId = relationshipId
    )
  }

  private suspend fun syncInheritanceClaims(
    account: FullAccount,
  ): Result<InheritanceClaims, Error> =
    coroutineBinding {
      val inheritanceClaims = retrieveInheritanceClaimsF8EClient.retrieveInheritanceClaims(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId
      ).bind()

      inheritanceClaimsDao.setInheritanceClaims(inheritanceClaims).bind()

      inheritanceClaims
    }
}
