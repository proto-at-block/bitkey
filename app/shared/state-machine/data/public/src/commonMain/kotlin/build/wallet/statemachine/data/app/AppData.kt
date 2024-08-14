package build.wallet.statemachine.data.app

import build.wallet.statemachine.data.keybox.AccountData

/**
 * Describes app-scoped data.
 */
sealed interface AppData {
  /**
   * App-scoped data is loaded.
   */
  data object LoadingAppData : AppData

  /**
   * App-scoped data is loaded.
   *
   * @property accountData keybox data (no keybox, has keybox, etc).
   */
  data class AppLoadedData(
    val accountData: AccountData,
  ) : AppData
}
