import Shared
import UIKit
import SafariServices

class InAppBrowserNavigatorImpl: NSObject, InAppBrowserNavigator, SFSafariViewControllerDelegate {
    
    // MARK: - Private Properties
    
    // We store a reference to the `appViewController` so we can display the in-app browser on top when needed
    private let appViewController: UINavigationController
    
    //We store a reference to the `appViewController` so we can display the in-app browser on top when needed
    private var onCloseBrowser: (() -> Void)? = nil
    
    // MARK: - Life Cycle
    
    init(appViewController: UINavigationController) {
        self.appViewController = appViewController
    }
        
    /**
     * Will open a safari browser that is embedded within the app
     */
    func open(url: String, onClose: @escaping () -> Void) {
        guard let topViewController = appViewController.topViewController, topViewController.presentedViewController as? SFSafariViewController == nil else {
            return
        }
        self.onCloseBrowser = onClose
        let vc = SFSafariViewController(url: URL(string: url)!)
        vc.delegate = self
        topViewController.present(vc, animated: true)
    }
    
    func onClose() {
        onCloseBrowser?()
        onCloseBrowser = nil
    }
    
    // MARK: - SFSafariViewControllerDelegate
    
    func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
        onClose()
    }
}
