package build.wallet

import platform.UIKit.UIViewController

/**
 * Used to render the iOS app entirely with Compose Multiplatform UI.
 */
interface ComposeIosAppUIController {
  val viewController: UIViewController
}
