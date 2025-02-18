package build.wallet.platform.web

import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.SafariServices.SFSafariViewControllerDelegateProtocol
import platform.UIKit.UIWindow
import platform.darwin.NSObject

class InAppBrowserNavigatorImpl(
  private val window: UIWindow,
) : InAppBrowserNavigator {
  private var onCloseBrowser: (() -> Unit)? = null
  private var safariViewController: SFSafariViewController? = null
  private val delegate = SafariViewControllerDelegate()

  /**
   * Will open a safari browser that is embedded within the app
   */
  override fun open(
    url: String,
    onClose: () -> Unit,
  ) {
    val topViewController = window.rootViewController ?: return
    if (topViewController.presentedViewController is SFSafariViewController) {
      return
    }
    onCloseBrowser = onClose
    val vc = SFSafariViewController(uRL = NSURL(string = url))
    safariViewController = vc
    vc.delegate = delegate

    if (topViewController.presentedViewController?.isBeingDismissed() == true) {
      topViewController.presentedViewController
        ?.presentViewController(vc, animated = true, completion = null)
    } else {
      topViewController.presentViewController(vc, animated = true, completion = null)
    }
  }

  override fun onClose() {
    safariViewController = null
    onCloseBrowser?.invoke()
    onCloseBrowser = null
  }

  override fun close() {
    safariViewController?.dismissModalViewControllerAnimated(animated = true)
    onClose()
  }

  private inner class SafariViewControllerDelegate :
    NSObject(),
    SFSafariViewControllerDelegateProtocol {
    override fun safariViewControllerDidFinish(controller: SFSafariViewController) {
      onClose()
    }
  }
}
