package bitkey.securitycenter

import build.wallet.recovery.keyset.SpendingKeysetSyncStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class SpendingKeysetSyncActionTests : FunSpec({

  test("returns REPAIR_KEYSET_MISMATCH recommendation when status is Mismatch") {
    val action = SpendingKeysetSyncAction(
      syncStatus = SpendingKeysetSyncStatus.Mismatch(
        localActiveKeysetId = "local-keyset-id",
        serverActiveKeysetId = "server-keyset-id"
      )
    )

    action.getRecommendations().shouldContainExactly(
      SecurityActionRecommendation.REPAIR_KEYSET_MISMATCH
    )
  }

  test("returns empty recommendations when status is Synced") {
    val action = SpendingKeysetSyncAction(
      syncStatus = SpendingKeysetSyncStatus.Synced
    )

    action.getRecommendations().shouldBeEmpty()
  }

  test("returns empty recommendations when status is Unknown") {
    val action = SpendingKeysetSyncAction(
      syncStatus = SpendingKeysetSyncStatus.Unknown(error = RuntimeException("Network error"))
    )

    action.getRecommendations().shouldBeEmpty()
  }

  test("returns RECOVERY category") {
    val action = SpendingKeysetSyncAction(
      syncStatus = SpendingKeysetSyncStatus.Synced
    )

    action.category().shouldBe(SecurityActionCategory.RECOVERY)
  }

  test("returns KEYSET_SYNC type") {
    val action = SpendingKeysetSyncAction(
      syncStatus = SpendingKeysetSyncStatus.Synced
    )

    action.type().shouldBe(SecurityActionType.KEYSET_SYNC)
  }

  test("returns HasCriticalActions state when status is Mismatch") {
    val action = SpendingKeysetSyncAction(
      syncStatus = SpendingKeysetSyncStatus.Mismatch(
        localActiveKeysetId = "local-keyset-id",
        serverActiveKeysetId = "server-keyset-id"
      )
    )

    action.state().shouldBe(SecurityActionState.HasCriticalActions)
  }

  test("returns Secure state when status is Synced") {
    val action = SpendingKeysetSyncAction(
      syncStatus = SpendingKeysetSyncStatus.Synced
    )

    action.state().shouldBe(SecurityActionState.Secure)
  }

  test("returns Secure state when status is Unknown") {
    val action = SpendingKeysetSyncAction(
      syncStatus = SpendingKeysetSyncStatus.Unknown(error = RuntimeException("Network error"))
    )

    action.state().shouldBe(SecurityActionState.Secure)
  }
})
