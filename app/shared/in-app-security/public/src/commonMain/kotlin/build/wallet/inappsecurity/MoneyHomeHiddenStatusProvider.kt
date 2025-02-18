package build.wallet.inappsecurity

import build.wallet.inappsecurity.MoneyHomeHiddenStatus.HIDDEN
import build.wallet.inappsecurity.MoneyHomeHiddenStatus.VISIBLE
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages and provides the visibility status of transaction and balance info on
 * the money home screen.
 */
interface MoneyHomeHiddenStatusProvider {
  /**
   * The current hidden status of the money home screen
   */
  val hiddenStatus: StateFlow<MoneyHomeHiddenStatus>

  /**
   * Toggle the status between [HIDDEN] and [VISIBLE].
   */
  fun toggleStatus()
}

/**
 * Determines if current wallet balance should be shown (btc and fiat amount) or hidden (as ****).
 */
enum class MoneyHomeHiddenStatus {
  HIDDEN,
  VISIBLE,
}
