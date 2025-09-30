package build.wallet.platform.links

/**
 * Provides platform-specific app store URLs for directing users to update the Bitkey app.
 */
interface AppStoreUrlProvider {
  /**
   * Returns the app store URL for the current platform where users can download
   * or update the Bitkey app.
   */
  fun getAppStoreUrl(): String
}
