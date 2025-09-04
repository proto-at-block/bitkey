package build.wallet.fwup

import kotlinx.coroutines.flow.StateFlow

interface FwupDataDaoProvider {
  /**
   * Returns a StateFlow of FwupDataDao that emits when the DAO switches
   * between real and fake based on the account configuration.
   */
  fun get(): StateFlow<FwupDataDao>
}
