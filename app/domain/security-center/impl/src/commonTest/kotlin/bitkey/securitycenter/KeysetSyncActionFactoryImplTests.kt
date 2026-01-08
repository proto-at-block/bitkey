package bitkey.securitycenter

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.KeysetRepairFeatureFlag
import build.wallet.recovery.keyset.SpendingKeysetRepairServiceFake
import build.wallet.recovery.keyset.SpendingKeysetSyncStatus
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class KeysetSyncActionFactoryImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val spendingKeysetRepairService = SpendingKeysetRepairServiceFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val keysetRepairFeatureFlag = KeysetRepairFeatureFlag(featureFlagDao)

  fun factory() =
    KeysetSyncActionFactoryImpl(
      spendingKeysetRepairService = spendingKeysetRepairService,
      accountService = accountService,
      keysetRepairFeatureFlag = keysetRepairFeatureFlag
    )

  beforeTest {
    accountService.reset()
    spendingKeysetRepairService.reset()
    featureFlagDao.reset()
    // Enable feature flag by default
    keysetRepairFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
  }

  test("returns action with REPAIR_KEYSET_MISMATCH recommendation when mismatch detected and flag enabled") {
    accountService.setActiveAccount(FullAccountMock)
    spendingKeysetRepairService.setStatus(
      SpendingKeysetSyncStatus.Mismatch(
        localActiveKeysetId = "local-keyset-id",
        serverActiveKeysetId = "server-keyset-id"
      )
    )

    factory().create().test {
      val action = awaitItem()
      action.type().shouldBe(SecurityActionType.KEYSET_SYNC)
      action.category().shouldBe(SecurityActionCategory.RECOVERY)
      action.state().shouldBe(SecurityActionState.HasCriticalActions)
      action.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.REPAIR_KEYSET_MISMATCH
      )
    }
  }

  test("returns action with no recommendations when synced") {
    accountService.setActiveAccount(FullAccountMock)
    spendingKeysetRepairService.setStatus(SpendingKeysetSyncStatus.Synced)

    factory().create().test {
      val action = awaitItem()
      action.type().shouldBe(SecurityActionType.KEYSET_SYNC)
      action.state().shouldBe(SecurityActionState.Secure)
      action.getRecommendations().shouldBeEmpty()
    }
  }

  test("returns action with no recommendations when feature flag is disabled") {
    keysetRepairFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
    accountService.setActiveAccount(FullAccountMock)
    spendingKeysetRepairService.setStatus(
      SpendingKeysetSyncStatus.Mismatch(
        localActiveKeysetId = "local-keyset-id",
        serverActiveKeysetId = "server-keyset-id"
      )
    )

    factory().create().test {
      val action = awaitItem()
      action.type().shouldBe(SecurityActionType.KEYSET_SYNC)
      action.state().shouldBe(SecurityActionState.Secure)
      action.getRecommendations().shouldBeEmpty()
    }
  }

  test("returns action with no recommendations when no full account") {
    accountService.accountState.value = Ok(AccountStatus.NoAccount)
    spendingKeysetRepairService.setStatus(
      SpendingKeysetSyncStatus.Mismatch(
        localActiveKeysetId = "local-keyset-id",
        serverActiveKeysetId = "server-keyset-id"
      )
    )

    factory().create().test {
      val action = awaitItem()
      action.type().shouldBe(SecurityActionType.KEYSET_SYNC)
      action.state().shouldBe(SecurityActionState.Secure)
      action.getRecommendations().shouldBeEmpty()
    }
  }

  test("returns action with no recommendations when lite account") {
    accountService.setActiveAccount(LiteAccountMock)
    spendingKeysetRepairService.setStatus(
      SpendingKeysetSyncStatus.Mismatch(
        localActiveKeysetId = "local-keyset-id",
        serverActiveKeysetId = "server-keyset-id"
      )
    )

    factory().create().test {
      val action = awaitItem()
      action.type().shouldBe(SecurityActionType.KEYSET_SYNC)
      action.state().shouldBe(SecurityActionState.Secure)
      action.getRecommendations().shouldBeEmpty()
    }
  }

  test("returns action with no recommendations when sync status is unknown") {
    accountService.setActiveAccount(FullAccountMock)
    spendingKeysetRepairService.setStatus(
      SpendingKeysetSyncStatus.Unknown(error = RuntimeException("Network error"))
    )

    factory().create().test {
      val action = awaitItem()
      action.type().shouldBe(SecurityActionType.KEYSET_SYNC)
      action.state().shouldBe(SecurityActionState.Secure)
      action.getRecommendations().shouldBeEmpty()
    }
  }

  test("action updates when sync status changes") {
    accountService.setActiveAccount(FullAccountMock)
    spendingKeysetRepairService.setStatus(SpendingKeysetSyncStatus.Synced)

    factory().create().test {
      // Initial synced state
      awaitItem().apply {
        state().shouldBe(SecurityActionState.Secure)
        getRecommendations().shouldBeEmpty()
      }

      // Change to mismatch
      spendingKeysetRepairService.setStatus(
        SpendingKeysetSyncStatus.Mismatch(
          localActiveKeysetId = "local-keyset-id",
          serverActiveKeysetId = "server-keyset-id"
        )
      )

      awaitItem().apply {
        state().shouldBe(SecurityActionState.HasCriticalActions)
        getRecommendations().shouldContainExactly(
          SecurityActionRecommendation.REPAIR_KEYSET_MISMATCH
        )
      }

      // Change back to synced
      spendingKeysetRepairService.setStatus(SpendingKeysetSyncStatus.Synced)

      awaitItem().apply {
        state().shouldBe(SecurityActionState.Secure)
        getRecommendations().shouldBeEmpty()
      }
    }
  }

  test("action updates when feature flag changes") {
    accountService.setActiveAccount(FullAccountMock)
    spendingKeysetRepairService.setStatus(
      SpendingKeysetSyncStatus.Mismatch(
        localActiveKeysetId = "local-keyset-id",
        serverActiveKeysetId = "server-keyset-id"
      )
    )

    factory().create().test {
      // Initially flag enabled - should show recommendation
      awaitItem().apply {
        state().shouldBe(SecurityActionState.HasCriticalActions)
        getRecommendations().shouldContainExactly(
          SecurityActionRecommendation.REPAIR_KEYSET_MISMATCH
        )
      }

      // Disable flag
      keysetRepairFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

      awaitItem().apply {
        state().shouldBe(SecurityActionState.Secure)
        getRecommendations().shouldBeEmpty()
      }

      // Re-enable flag
      keysetRepairFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

      awaitItem().apply {
        state().shouldBe(SecurityActionState.HasCriticalActions)
        getRecommendations().shouldContainExactly(
          SecurityActionRecommendation.REPAIR_KEYSET_MISMATCH
        )
      }
    }
  }
})
