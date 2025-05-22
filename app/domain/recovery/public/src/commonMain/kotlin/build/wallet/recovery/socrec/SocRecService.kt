package build.wallet.recovery.socrec

import bitkey.relationships.Relationships
import build.wallet.bitkey.account.FullAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain service for managing Social Recovery operations.
 */
interface SocRecService {
  /**
   * Emits latest [Relationships] stored in the database by filtering [RelationshipsService.relationships]
   * by the [TrustedContactRole.SOCIAL_RECOVERY_CONTACT].
   *
   * For [FullAccount], the relationships are always verified before being emitted.
   *
   * Emits `null` on initial loading.
   * Emits [Relationships.EMPTY] if there was an error loading relationships from the database.
   */
  val socRecRelationships: StateFlow<Relationships?>

  /**
   * Emits true if the Customer just completed Social Recovery through
   * a Recovery Contact, in the same app session (without closing the app).
   * At this point, the Customer needs to complete the hardware replacement, unless
   * they have found their existing hardware.
   */
  fun justCompletedRecovery(): Flow<Boolean>
}
