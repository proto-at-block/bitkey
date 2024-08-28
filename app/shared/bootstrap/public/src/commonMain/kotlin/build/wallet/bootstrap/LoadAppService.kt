package build.wallet.bootstrap

/**
 * Domain service for loading initial app state.
 *
 * The service is meant to be used by the app on startup to determine the initial state.
 */
interface LoadAppService {
  /**
   * Loads the initial app state. This should be called on the app startup.
   * The [AppState] represents the high level state of the app (for example
   * if there's an active account or not).
   */
  suspend fun loadAppState(): AppState
}
