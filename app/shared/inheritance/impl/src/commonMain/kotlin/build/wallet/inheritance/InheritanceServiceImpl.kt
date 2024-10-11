package build.wallet.inheritance

import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.ensure
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.inheritance.UploadInheritanceMaterialF8eClient
import build.wallet.f8e.relationships.Relationships
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.relationships.RelationshipsService
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.first

class InheritanceServiceImpl(
  private val accountService: AccountService,
  private val relationshipsService: RelationshipsService,
  appCoroutineScope: CoroutineScope,
  private val inheritanceSyncDao: InheritanceSyncDao,
  private val inheritanceMaterialF8eClient: UploadInheritanceMaterialF8eClient,
  private val inheritanceMaterialCreator: InheritanceMaterialCreator,
) : InheritanceService {
  override val inheritanceRelationships: StateFlow<Relationships?> = relationshipsService.relationships
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
      val currentMaterialHash = inheritanceMaterialCreator.getInheritanceMaterialHash(keybox).bind()

      if (lastSyncHash == currentMaterialHash) {
        log(LogLevel.Debug) { "Inheritance Material is up-to-date. Skipping inheritance material sync" }
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

      inheritanceSyncDao.updateSyncedInheritanceMaterialHash(currentMaterialHash).bind()
    }
}
