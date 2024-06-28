package build.wallet.inappsecurity

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
   * Toggle the status between [MoneyHomeHiddenStatus.HIDDEN] and [MoneyHomeHiddenStatus.VISIBLE]
   */
  fun toggleStatus()
}

enum class MoneyHomeHiddenStatus {
  HIDDEN,
  VISIBLE,
}
