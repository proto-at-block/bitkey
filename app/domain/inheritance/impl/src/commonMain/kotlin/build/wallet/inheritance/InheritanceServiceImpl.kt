package build.wallet.inheritance

import bitkey.relationships.Relationships
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.signChallenge
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.challange.DelayNotifyChallenge
import build.wallet.bitkey.inheritance.BenefactorClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.bitkey.inheritance.isApproved
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.*
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.inheritance.*
import build.wallet.logging.logDebug
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.relationships.RelationshipsService
import build.wallet.worker.BackgroundStrategy
import build.wallet.worker.RunStrategy
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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
  private val cancelInheritanceClaimF8eClient: CancelInheritanceClaimF8eClient,
  private val completeInheritanceClaimF8eClient: CompleteInheritanceClaimF8eClient,
  private val transactionFactory: InheritanceTransactionFactory,
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val inheritanceClaimsRepository: InheritanceClaimsRepository,
  private val clock: Clock,
  inheritanceSyncFrequency: InheritanceSyncFrequency,
) : InheritanceService, InheritanceClaimsSyncWorker {
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

  override val claimsSnapshot: Flow<ClaimsSnapshot> =
    inheritanceClaimsRepository.getClaimsSnapshot(clock)

  override val beneficiaryClaimState: Flow<ImmutableList<ContactClaimState.Beneficiary>> = combine(
    inheritanceRelationships.map { it.endorsedTrustedContacts + it.invitations },
    inheritanceClaimsRepository.getClaimsSnapshot(clock)
  ) { relationships, snapshot ->
    relationships.map { relationship ->
      ContactClaimState.Beneficiary(
        timestamp = snapshot.timestamp,
        relationship = relationship,
        claims = snapshot.claims.all.filter { it.relationshipId == relationship.id },
        isInvite = relationship is Invitation
      )
    }
  }.map { it.toImmutableList() }

  override val benefactorClaimState: Flow<ImmutableList<ContactClaimState.Benefactor>> = combine(
    inheritanceRelationships.map { it.protectedCustomers },
    inheritanceClaimsRepository.getClaimsSnapshot(clock)
  ) { relationships, snapshot ->
    relationships.map { relationship ->
      ContactClaimState.Benefactor(
        timestamp = snapshot.timestamp,
        relationship = relationship,
        claims = snapshot.claims.all.filter { it.relationshipId == relationship.id }
      )
    }
  }.map { it.toImmutableList() }

  override val runStrategy: Set<RunStrategy> = setOf(
    RunStrategy.Startup(),
    RunStrategy.Periodic(
      interval = inheritanceSyncFrequency.value,
      backgroundStrategy = BackgroundStrategy.Wait
    )
  )

  override suspend fun executeWork() {
    inheritanceClaimsRepository.syncServerClaims()
  }

  override suspend fun createInheritanceInvitation(
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
  ): Result<OutgoingInvitation, Error> =
    coroutineBinding {
      val account = accountService.getAccount<FullAccount>().bind()

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

      val account = accountService.getAccount<FullAccount>()
        .logFailure { "No full-account found. Skipping inheritance material sync" }
        .bind()

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
  ): Result<BeneficiaryClaim.PendingClaim, Throwable> =
    coroutineBinding {
      val account = accountService.getAccount<FullAccount>()
        .logFailure { "Start claim cannot be called without full account." }
        .bind()

      val claim = startInheritanceClaimF8eClient
        .startInheritanceClaim(account, relationshipId)
        .bind()

      inheritanceClaimsRepository.updateSingleClaim(claim)

      claim
    }

  override suspend fun loadApprovedClaim(
    relationshipId: RelationshipId,
  ): Result<InheritanceTransactionDetails, Throwable> =
    coroutineBinding {
      val account = accountService.getAccount<FullAccount>().bind()

      val allClaims = inheritanceClaimsRepository.fetchClaims().bind()
      val completableClaims = allClaims.beneficiaryClaims
        .filter { it.relationshipId == relationshipId }
        .filter { it.isApproved(clock.now()) }
      when {
        completableClaims.isEmpty() -> Err(Error("Claim not found")).bind()
        completableClaims.size > 1 -> logError {
          "Multiple claims in completable state. Continuing with first."
        }
      }
      val claim = completableClaims.first()

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
      val account = accountService.getAccount<FullAccount>().bind()

      completeInheritanceClaimF8eClient.completeInheritanceClaim(
        fullAccount = account,
        claimId = details.claim.claimId,
        psbt = details.psbt
      ).bind().also {
        inheritanceClaimsRepository.updateSingleClaim(it)
      }
    }

  override suspend fun completeClaimWithoutTransfer(
    relationshipId: RelationshipId,
  ): Result<BeneficiaryClaim.CompleteClaim, Throwable> {
    return coroutineBinding {
      val account = accountService.getAccount<FullAccount>().bind()

      val claim = inheritanceClaimsRepository.fetchClaims().bind()
        .beneficiaryClaims
        .filterIsInstance<BeneficiaryClaim.LockedClaim>()
        .let {
          when {
            it.size == 1 -> it.single()
            it.isNotEmpty() -> it.first().also {
              logError { "Multiple locked claims found. Continuing with first." }
            }
            else -> Err(Error("No locked claims to complete")).bind()
          }
        }

      completeInheritanceClaimF8eClient.completeInheritanceClaimWithoutTransfer(
        fullAccount = account,
        claimId = claim.claimId
      ).bind().also {
        inheritanceClaimsRepository.updateSingleClaim(it)
      }
    }
  }

  override suspend fun cancelClaims(relationshipId: RelationshipId): Result<Unit, Throwable> =
    coroutineBinding {
      val account = accountService.getAccount<FullAccount>().bind()

      val allClaims = inheritanceClaimsRepository.fetchClaims().bind()
      val claims = (allClaims.beneficiaryClaims + allClaims.benefactorClaims)
        .filter { it is BeneficiaryClaim.PendingClaim || it is BenefactorClaim.PendingClaim }
        .filter { it.relationshipId == relationshipId }
        .takeIf { it.isNotEmpty() }
        ?: Err(InheritanceService.ClaimNotFoundError("Claim not found")).bind()

      claims.forEach { claim ->
        val canceledClaim = cancelInheritanceClaimF8eClient.cancelClaim(
          fullAccount = account,
          inheritanceClaim = claim
        ).bind()

        inheritanceClaimsRepository.updateSingleClaim(canceledClaim)
      }
    }
}
