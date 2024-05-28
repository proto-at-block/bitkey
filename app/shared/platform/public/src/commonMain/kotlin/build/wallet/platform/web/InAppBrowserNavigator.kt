package build.wallet.platform.web

/**
 * Used to open an In App Browser instance
 */
interface InAppBrowserNavigator {
  /**
   * Open url link in platform's browser that will be embedded in the app.
   */
  fun open(
    url: String,
    onClose: () -> Unit,
  )

  /**
   * Gets invoked when the browser closes.
   */
  fun onClose()

  /**
   * close the in-app browser
   */
  fun close()
}
