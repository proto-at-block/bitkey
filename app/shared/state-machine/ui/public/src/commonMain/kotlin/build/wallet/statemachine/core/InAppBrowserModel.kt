package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.EventTrackerScreenInfo

/**
 * Model used to launch an in-app browser within the app.
 * @property urlString: The starting url used to open the in-app browser.
 * @property browserNavigator Used to launch the browser, implementation will differ per platform.
 * @property onClose callback fired when the browser is closed.
 */
class InAppBrowserModel(
  val open: () -> Unit,
) : BodyModel() {
  // There will be no eventTrackerScreenInfo due to privacy policies
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null
}
