package build.wallet.debug

import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.F8eEnvironment.Staging
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.*
import kotlin.time.Duration.Companion.seconds

interface DefaultDebugOptionsDecider {
  /**
   * Determines the default initial options to use based on the app variant.
   */
  val options: DebugOptions
}

@BitkeyInject(AppScope::class)
class DefaultDebugOptionsDeciderImpl(
  private val appVariant: AppVariant,
) : DefaultDebugOptionsDecider {
  override val options: DebugOptions by lazy {
    when (appVariant) {
      Development -> DebugOptions(
        bitcoinNetworkType = SIGNET,
        isHardwareFake = true,
        f8eEnvironment = Staging,
        isTestAccount = true,
        isUsingSocRecFakes = false,
        delayNotifyDuration = 20.seconds,
        skipNotificationsOnboarding = false,
        skipCloudBackupOnboarding = false
      )
      Alpha -> DebugOptions(
        bitcoinNetworkType = BITCOIN,
        isHardwareFake = false,
        f8eEnvironment = Production,
        isTestAccount = true,
        isUsingSocRecFakes = false,
        delayNotifyDuration = 20.seconds,
        skipNotificationsOnboarding = false,
        skipCloudBackupOnboarding = false
      )
      Team -> DebugOptions(
        bitcoinNetworkType = BITCOIN,
        isHardwareFake = false,
        f8eEnvironment = Production,
        isTestAccount = true,
        isUsingSocRecFakes = false,
        delayNotifyDuration = 20.seconds,
        skipNotificationsOnboarding = false,
        skipCloudBackupOnboarding = false
      )
      Beta -> DebugOptions(
        bitcoinNetworkType = BITCOIN,
        isHardwareFake = false,
        f8eEnvironment = Production,
        isTestAccount = false,
        isUsingSocRecFakes = false,
        delayNotifyDuration = null,
        skipNotificationsOnboarding = false,
        skipCloudBackupOnboarding = false
      )
      Customer -> DebugOptions(
        bitcoinNetworkType = BITCOIN,
        isHardwareFake = false,
        f8eEnvironment = Production,
        isTestAccount = false,
        isUsingSocRecFakes = false,
        delayNotifyDuration = null,
        skipNotificationsOnboarding = false,
        skipCloudBackupOnboarding = false
      )
      Emergency -> DebugOptions(
        bitcoinNetworkType = BITCOIN,
        isHardwareFake = false,
        f8eEnvironment = F8eEnvironment.ForceOffline,
        isTestAccount = false,
        isUsingSocRecFakes = false,
        delayNotifyDuration = null,
        skipNotificationsOnboarding = false,
        skipCloudBackupOnboarding = false
      )
    }
  }
}
