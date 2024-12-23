package build.wallet.inheritance

import build.wallet.account.AccountService
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.signChallenge
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.challange.DelayNotifyChallenge
import build.wallet.bitkey.inheritance.BenefactorClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.inheritance.CompleteInheritanceClaimF8eClient
import build.wallet.f8e.inheritance.LockInheritanceClaimF8eClient
import build.wallet.f8e.inheritance.StartInheritanceClaimF8eClient
import build.wallet.f8e.inheritance.UploadInheritanceMaterialF8eClient
import build.wallet.f8e.relationships.Relationships
import build.wallet.logging.logDebug
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.relationships.RelationshipsService
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

@BitkeyInject(AppScope::class)
class InheritanceServiceImpl(
  private val accountService: AccountService,
  private val relationshipsService: RelationshipsService,
  private val inheritanceSyncDao: InheritanceSyncDao,
  private val inheritanceMaterialF8eClient: UploadInheritanceMaterialF8eClient,
  private val startInheritanceClaimF8eClient: StartInheritanceClaimF8eClient,
  private val inheritanceCrypto: InheritanceCrypto,
  private val lockInheritanceClaimF8eClient: LockInheritanceClaimF8eClient,
  private val completeInheritanceClaimF8eClient: CompleteInheritanceClaimF8eClient,
  private val transactionFactory: InheritanceTransactionFactory,
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val inheritanceClaimsRepository: InheritanceClaimsRepository,
  private val clock: Clock,
) : InheritanceService, InheritanceClaimsSyncWorker {
  override val relationshipsWithPendingClaim: Flow<List<RelationshipId>> = inheritanceClaimsRepository.claims
    .map { result ->
      result.get()?.all.orEmpty().map { it.relationshipId }
    }

  override val claims: Flow<List<InheritanceClaim>> =
    inheritanceClaimsRepository.claims
      .map { it.get()?.all.orEmpty() }

  override val inheritanceRelationships: Flow<Relationships> =
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

  override val relationshipsWithNoActiveClaims: Flow<List<RelationshipId>> = combine(
    inheritanceClaimsRepository.claims,
    inheritanceRelationships
  ) { claims, relationships ->
    relationships.endorsedTrustedContacts.map {
      it.id
    } + relationships.protectedCustomers.map { RelationshipId(it.relationshipId) }
      .filter { it !in claims.get()?.all.orEmpty().map { it.relationshipId } }
  }

  override val relationshipsWithCancelableClaim: Flow<List<RelationshipId>> = inheritanceClaimsRepository.claims
    .map { it.get()?.all.orEmpty() }
    .map { it.filter { it is BeneficiaryClaim.PendingClaim || it is BenefactorClaim.PendingClaim } }
    .map { it.map { it.relationshipId } }

  override val relationshipsWithCompletableClaim: Flow<List<RelationshipId>> = inheritanceClaimsRepository.claims
    .map { it.get()?.all.orEmpty() }
    .map { it.filter { it.isCompletable() } }
    .map { it.map { it.relationshipId } }

  /**
   * Determine if a claim is in a completable state.
   */
  private fun InheritanceClaim.isCompletable(): Boolean {
    if (this is BeneficiaryClaim.LockedClaim || this is BenefactorClaim.LockedClaim) {
      return true
    }

    val completeTime = when (this) {
      is BeneficiaryClaim.PendingClaim -> delayEndTime
      is BenefactorClaim.PendingClaim -> delayEndTime
      else -> return false
    }

    return clock.now() > completeTime
  }

  override suspend fun executeWork() {
    // Attempt an initial one-time sync at startup.
    inheritanceClaimsRepository.fetchClaims()
      .logFailure { "Unable to sync Inheritance Claims" }
  }

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
        inheritanceCrypto.getInheritanceMaterialHashData(keybox).bind()

      if (lastSyncHash == currentMaterialHashData.inheritanceMaterialHash) {
        logDebug { "Inheritance Material is up-to-date. Skipping inheritance material sync" }
        return@coroutineBinding
      }

      if (currentMaterialHashData.contacts.isEmpty() && lastSyncHash == null) {
        logDebug {
          "No inheritance contacts to sync initial data. Skipping inheritance material sync"
        }
        return@coroutineBinding
      }

      val account = accountService.activeAccount().first()
      if (account !is FullAccount) {
        logDebug { "No full-account found. Skipping inheritance material sync" }
        return@coroutineBinding
      }

      val inheritanceMaterial = inheritanceCrypto.createInheritanceMaterial(keybox).bind()

      inheritanceMaterialF8eClient.uploadInheritanceMaterial(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId,
        inheritanceMaterial = inheritanceMaterial
      ).mapError { Error("Failed inheritance material server-sync", it) }
        .onSuccess {
          logDebug { "Inheritance Material Sync Successful" }
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
      logError { "Start claim cannot be called without full account." }
      return Err(Error("Not a full account"))
    }

    return startInheritanceClaimF8eClient.startInheritanceClaim(
      fullAccount = account,
      relationshipId = relationshipId
    )
  }

  override suspend fun loadApprovedClaim(
    relationshipId: RelationshipId,
  ): Result<InheritanceTransactionDetails, Throwable> =
    coroutineBinding {
      val account = accountService.activeAccount().first()
      if (account !is FullAccount) {
        error("load claim cannot be called without full account.")
      }
      val allClaims = inheritanceClaimsRepository.fetchClaims().bind()
      val claim = allClaims.beneficiaryClaims.find {
        it.relationshipId == relationshipId
      } ?: error("Claim not found")

      val challenge = DelayNotifyChallenge.fromKeybox(
        type = DelayNotifyChallenge.Type.INHERITANCE,
        keybox = account.keybox
      )

      val signedChallenge = appAuthKeyMessageSigner.signChallenge(
        publicKey = account.keybox.activeAppKeyBundle.authKey,
        challenge = challenge
      ).bind()

      val lockedClaim = if (claim is BeneficiaryClaim.LockedClaim) {
        claim
      } else {
        lockInheritanceClaimF8eClient.lockClaim(
          fullAccount = account,
          relationshipId = claim.relationshipId,
          inheritanceClaimId = claim.claimId,
          signedChallenge = signedChallenge
        ).bind()
      }

      inheritanceClaimsRepository.updateSingleClaim(lockedClaim)

      transactionFactory.createFullBalanceTransaction(
        account = account,
        claim = lockedClaim
      ).bind()
    }

  override suspend fun completeClaimTransfer(
    relationshipId: RelationshipId,
    details: InheritanceTransactionDetails,
  ): Result<BeneficiaryClaim.CompleteClaim, Throwable> =
    coroutineBinding {
      val account = accountService.activeAccount().first()
      if (account !is FullAccount) {
        error("complete claim cannot be called without full account.")
      }

      completeInheritanceClaimF8eClient.completeInheritanceClaim(
        fullAccount = account,
        claimId = details.claim.claimId,
        psbt = details.psbt
      ).bind()
    }
}
