package build.wallet.recovery.socrec

import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.f8e.relationships.Relationships
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementScreens
import build.wallet.relationships.RelationshipsService
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly

class SocRecServiceImpl(
  private val postSocRecTaskRepository: PostSocRecTaskRepository,
  relationshipsService: RelationshipsService,
  appCoroutineScope: CoroutineScope,
) : SocRecService {
  override val socRecRelationships = relationshipsService.relationships
    .filterNotNull()
    .map { relationships ->
      Relationships(
        invitations = relationships.invitations
          .filter { it.roles.contains(TrustedContactRole.SocialRecoveryContact) },
        endorsedTrustedContacts = relationships.endorsedTrustedContacts
          .filter { it.roles.contains(TrustedContactRole.SocialRecoveryContact) },
        unendorsedTrustedContacts = relationships.unendorsedTrustedContacts
          .filter { it.roles.contains(TrustedContactRole.SocialRecoveryContact) },
        protectedCustomers = relationships.protectedCustomers
          .filter { it.roles.contains(TrustedContactRole.SocialRecoveryContact) }
          .toImmutableList()
      )
    }
    .stateIn(appCoroutineScope, Eagerly, null)

  override fun justCompletedRecovery(): Flow<Boolean> {
    return postSocRecTaskRepository.taskState
      .map { it == HardwareReplacementScreens }
      .distinctUntilChanged()
  }
}
