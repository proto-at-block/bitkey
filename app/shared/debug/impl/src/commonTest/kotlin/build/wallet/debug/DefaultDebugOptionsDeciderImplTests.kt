package build.wallet.debug

import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.f8e.F8eEnvironment.*
import build.wallet.platform.config.AppVariant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class DefaultDebugOptionsDeciderImplTests : FunSpec({
  test("Customer app variant") {
    DefaultDebugOptionsDeciderImpl(AppVariant.Customer)
      .options.shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false,
          delayNotifyDuration = null,
          skipNotificationsOnboarding = false,
          skipCloudBackupOnboarding = false
        )
      )
  }

  test("Development app variant") {
    DefaultDebugOptionsDeciderImpl(AppVariant.Development)
      .options.shouldBe(
        DebugOptions(
          bitcoinNetworkType = SIGNET,
          isHardwareFake = true,
          f8eEnvironment = Staging,
          isTestAccount = true,
          isUsingSocRecFakes = false,
          delayNotifyDuration = 20.seconds,
          skipNotificationsOnboarding = false,
          skipCloudBackupOnboarding = false
        )
      )
  }

  test("Emergency app variant") {
    DefaultDebugOptionsDeciderImpl(AppVariant.Emergency)
      .options.shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = ForceOffline,
          isTestAccount = false,
          isUsingSocRecFakes = false,
          delayNotifyDuration = null,
          skipNotificationsOnboarding = false,
          skipCloudBackupOnboarding = false
        )
      )
  }

  test("Team app variant") {
    DefaultDebugOptionsDeciderImpl(AppVariant.Team)
      .options.shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = true,
          isUsingSocRecFakes = false,
          delayNotifyDuration = 20.seconds,
          skipNotificationsOnboarding = false,
          skipCloudBackupOnboarding = false
        )
      )
  }

  test("Beta app variant") {
    DefaultDebugOptionsDeciderImpl(AppVariant.Beta)
      .options.shouldBe(
        DebugOptions(
          bitcoinNetworkType = BITCOIN,
          isHardwareFake = false,
          f8eEnvironment = Production,
          isTestAccount = false,
          isUsingSocRecFakes = false,
          delayNotifyDuration = null,
          skipNotificationsOnboarding = false,
          skipCloudBackupOnboarding = false
        )
      )
  }
})
