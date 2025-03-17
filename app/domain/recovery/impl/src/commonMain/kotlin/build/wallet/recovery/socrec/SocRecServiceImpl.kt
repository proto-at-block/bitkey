package build.wallet.recovery.socrec

import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.bitkey.relationships.socialRecoveryProtectedCustomers
import build.wallet.bitkey.relationships.socialRecoveryTrustedContacts
import build.wallet.bitkey.relationships.socialRecoveryUnendorsedTrustedContacts
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementScreens
import build.wallet.relationships.RelationshipsService
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly

@BitkeyInject(AppScope::class)
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
        endorsedTrustedContacts = relationships.endorsedTrustedContacts.socialRecoveryTrustedContacts(),
        unendorsedTrustedContacts = relationships.unendorsedTrustedContacts.socialRecoveryUnendorsedTrustedContacts(),
        protectedCustomers = relationships.protectedCustomers.socialRecoveryProtectedCustomers()
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
