package build.wallet.fwup

import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Provides the sync frequency for firmware updates as a reactive [Flow].
 *
 * For fake hardware, syncs every 5 seconds to quickly detect version changes
 * after fake firmware updates complete.
 *
 * For real hardware, syncs every hour to avoid excessive network requests.
 *
 * The flow re-emits whenever the account configuration changes, allowing
 * consumers to restart their ticker with the new interval.
 */
@BitkeyInject(AppScope::class)
class FirmwareUpdateSyncFrequency(
  private val accountConfigService: AccountConfigService,
) {
  val value: Flow<Duration>
    get() = accountConfigService.activeOrDefaultConfig()
      .map { config ->
        val isHardwareFake = when (config) {
          is FullAccountConfig -> config.isHardwareFake
          is DefaultAccountConfig -> config.isHardwareFake
          else -> false
        }
        if (isHardwareFake) 5.seconds else 1.hours
      }
      .distinctUntilChanged()
}
