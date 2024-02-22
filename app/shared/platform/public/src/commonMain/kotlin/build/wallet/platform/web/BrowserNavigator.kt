package build.wallet.platform.web

/**
 * An abstraction around opening links in platforms' browser. This needs to be implemented by
 * each app's UI and passed to a state machine via model. See current usages for an example.
 */
fun interface BrowserNavigator {
  /**
   * Open url link in platform's browser.
   */
  fun open(url: String)
}
