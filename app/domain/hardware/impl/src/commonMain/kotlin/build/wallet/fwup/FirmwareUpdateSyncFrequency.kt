package build.wallet.fwup

import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Provides the sync frequency for firmware updates.
 *
 * For fake hardware, syncs every 5 seconds to quickly detect version changes
 * after fake firmware updates complete.
 *
 * For real hardware, syncs every hour to avoid excessive network requests.
 */
@BitkeyInject(AppScope::class)
class FirmwareUpdateSyncFrequency(
  private val accountConfigService: AccountConfigService,
) {
  val value: Duration
    get() {
      val isHardwareFake = when (val config = accountConfigService.activeOrDefaultConfig().value) {
        is FullAccountConfig -> config.isHardwareFake
        is DefaultAccountConfig -> config.isHardwareFake
        else -> false
      }
      return if (isHardwareFake) 5.seconds else 1.hours
    }
}
