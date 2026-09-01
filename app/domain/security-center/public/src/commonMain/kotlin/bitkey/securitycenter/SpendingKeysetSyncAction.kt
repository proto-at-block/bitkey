package bitkey.securitycenter

import build.wallet.recovery.keyset.SpendingKeysetSyncStatus

/**
 * Security action for spending keyset sync status.
 *
 * When wallet keyset data needs repair,
 * this action will recommend the user repair their wallet.
 */
data class SpendingKeysetSyncAction(
  private val syncStatus: SpendingKeysetSyncStatus,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> =
    when (syncStatus) {
      is SpendingKeysetSyncStatus.Mismatch,
      is SpendingKeysetSyncStatus.IncompleteKeysetList,
      is SpendingKeysetSyncStatus.IncompletePrivateWallet ->
        listOf(SecurityActionRecommendation.REPAIR_KEYSET_MISMATCH)
      // Intentionally no recommendation. The repair flow cannot resolve these keysets, so
      // recommending it produces a prompt the customer can complete repeatedly without ever
      // clearing the state -- the exact loop this status was introduced to stop. Surfaced via
      // telemetry (logWarn in SpendingKeysetRepairServiceImpl) so we can measure prevalence and
      // reach affected customers through support instead.
      is SpendingKeysetSyncStatus.IncompleteKeysetListUnrecoverable -> emptyList()
      else -> emptyList()
    }

  override fun category(): SecurityActionCategory = SecurityActionCategory.RECOVERY

  override fun type(): SecurityActionType = SecurityActionType.KEYSET_SYNC

  override fun state(): SecurityActionState =
    when (syncStatus) {
      is SpendingKeysetSyncStatus.Mismatch,
      is SpendingKeysetSyncStatus.IncompleteKeysetList,
      is SpendingKeysetSyncStatus.IncompletePrivateWallet ->
        SecurityActionState.HasCriticalActions
      // Not critical: there is no action the customer can take. See getRecommendations above.
      is SpendingKeysetSyncStatus.IncompleteKeysetListUnrecoverable -> SecurityActionState.Secure
      else -> SecurityActionState.Secure
    }
}
