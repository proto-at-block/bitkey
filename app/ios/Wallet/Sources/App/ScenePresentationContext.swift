import UIKit

final class ScenePresentationContext: @unchecked Sendable {
    private(set) weak var window: UIWindow?

    func attach(window: UIWindow) {
        self.window = window
    }

    func detach(window: UIWindow) {
        if self.window === window {
            self.window = nil
        }
    }

    var rootViewController: UIViewController? {
        window?.rootViewController
    }

    var topViewController: UIViewController? {
        var viewController = rootViewController
        while let presentedViewController = viewController?.presentedViewController {
            viewController = presentedViewController
        }
        return viewController
    }
}
