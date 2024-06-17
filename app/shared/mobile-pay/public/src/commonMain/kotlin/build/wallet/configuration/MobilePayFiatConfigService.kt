package build.wallet.configuration

import kotlinx.coroutines.flow.StateFlow

/**
 * Service to manage [MobilePayFiatConfig].
 */
interface MobilePayFiatConfigService {
  /**
   * Returns latest [MobilePayFiatConfig] for the fiat currency currently
   * selected by the customer as their primary currency.
   *
   * The configuration is synced by [MobilePayFiatConfigSyncWorker].
   */
  val config: StateFlow<MobilePayFiatConfig>
}
