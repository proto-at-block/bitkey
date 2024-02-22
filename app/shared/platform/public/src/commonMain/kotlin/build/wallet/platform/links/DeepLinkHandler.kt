package build.wallet.platform.links

/**
 * Used to handle links within the app
 */
interface DeepLinkHandler {
  /**
   * Will start a flow initiated by a deeplink.
   * Will show an alert if `appRestrictions` are not met after opening.
   */
  fun openDeeplink(
    url: String,
    appRestrictions: AppRestrictions?,
  ): OpenDeeplinkResult
}

/**
 * Result type for opening a deeplink using the [DeepLinkHandler]
 */
sealed interface OpenDeeplinkResult {
  /**
   * The deeplink was opened, `appRestrictionResult` specifies if there was any
   * issues with the version of the app that was opened.
   *
   * We open the app if app restrictions failed because we do not want to intercept a path
   * to a partner. If the `appRestrictionResult` Failed, we warn the user to update their app.
   */
  data class Opened(val appRestrictionResult: AppRestrictionResult) : OpenDeeplinkResult

  /**
   * The deeplink not opened
   */
  data object Failed : OpenDeeplinkResult

  /**
   * Specifies whether we successfully met the AppRestrictions if any applied
   */
  sealed interface AppRestrictionResult {
    /**
     * The app opened met the minimum app restriction requirement.
     */
    data object Success : AppRestrictionResult

    /**
     * The app version found was less than the minimum version than we require.
     */
    data class Failed(val appRestrictions: AppRestrictions) : AppRestrictionResult

    /**
     * Used if the app was not found, or for IOS
     */
    data object None : AppRestrictionResult
  }
}
