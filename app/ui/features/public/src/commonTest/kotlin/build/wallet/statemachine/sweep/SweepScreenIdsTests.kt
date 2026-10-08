package build.wallet.statemachine.sweep

import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.InactiveWalletSweepEventTrackerScreenId
import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.recovery.sweep.SweepContext
import build.wallet.statemachine.recovery.sweep.screenIds
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SweepScreenIdsTests : FunSpec({
  test("inactive wallet and inactive hardware use inactive wallet analytics") {
    listOf(
      SweepContext.InactiveWallet,
      SweepContext.InactiveHardware("test-fingerprint")
    ).forEach { context ->
      context.screenIds.generatingPsbts.shouldBe(
        InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_GENERATING_PSBTS
      )
      context.screenIds.hardwareVerificationRequired.shouldBe(
        InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_HARDWARE_VERIFICATION_REQUIRED
      )
    }
  }

  test("app recovery uses app recovery analytics") {
    SweepContext.Recovery(App).screenIds.success.shouldBe(
      DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS
    )
  }

  test("hardware recovery uses hardware recovery analytics") {
    SweepContext.Recovery(Hardware).screenIds.failed.shouldBe(
      HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_FAILED
    )
  }

  test("migration and W3 upgrade use migration analytics") {
    listOf(
      SweepContext.PrivateWalletMigration,
      SweepContext.W3Upgrade("test-fingerprint")
    ).forEach { context ->
      context.screenIds.signPsbtsPrompt.shouldBe(
        WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_SIGN_PSBTS_PROMPT
      )
    }
  }
})
