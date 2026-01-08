package bitkey.securitycenter

import build.wallet.recovery.keyset.SpendingKeysetSyncStatus

/**
 * Security action for spending keyset sync status.
 *
 * When a keyset mismatch is detected between local and server state,
 * this action will recommend the user repair their wallet.
 */
data class SpendingKeysetSyncAction(
  private val syncStatus: SpendingKeysetSyncStatus,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> =
    when (syncStatus) {
      is SpendingKeysetSyncStatus.Mismatch -> listOf(SecurityActionRecommendation.REPAIR_KEYSET_MISMATCH)
      else -> emptyList()
    }

  override fun category(): SecurityActionCategory = SecurityActionCategory.RECOVERY

  override fun type(): SecurityActionType = SecurityActionType.KEYSET_SYNC

  override fun state(): SecurityActionState =
    when (syncStatus) {
      is SpendingKeysetSyncStatus.Mismatch -> SecurityActionState.HasCriticalActions
      else -> SecurityActionState.Secure
    }
}
