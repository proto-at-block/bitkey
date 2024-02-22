import Shared
import UIKit

class SharingManagerImpl: SharingManager {

    public var mainWindow: UIWindow?

    // MARK: - SharingManager

    func shareText(text: String, title: String, completion: ((KotlinBoolean) -> Void)? = nil) {
        let ac = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        ac.completionWithItemsHandler = { _, completed, _, _ in
            if completed {
                completion?(KotlinBoolean(bool: completed))
            }
        }
        topViewController()?.present(ac, animated: true)
    }
    
    func completed() {
        // no-op on iOS
    }

    // MARK: - Private Methods

    private func topViewController() -> UIViewController? {
        guard var topController = mainWindow?.rootViewController else {
            return nil
        }

        while let presentedViewController = topController.presentedViewController {
            topController = presentedViewController
        }
        return topController
    }

}
