import UIKit

/**
 * A UINavigationController that hides its top navigation bar view.
 * It also contains logic to allow the interactive pop gesture when the navigation bar is hidden.
 */
open class HiddenBarNavigationController: UINavigationController, UIGestureRecognizerDelegate {

    public var supportsBackSwipeGesture: Bool = false

    // MARK: - Life Cycle

    public convenience init() {
        self.init(nibName: nil, bundle: nil)
        hideNavigationBar()
    }

    override public init(rootViewController: UIViewController) {
        super.init(rootViewController: rootViewController)
        hideNavigationBar()
    }

    override public init(nibName: String?, bundle: Bundle?) {
        super.init(nibName: nibName, bundle: bundle)
        hideNavigationBar()
    }

    @available(*, unavailable)
    public required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    // MARK: - UIViewController

    override public var shouldAutorotate: Bool {
        guard let firstChild = children.first else {
            return super.shouldAutorotate
        }
        return firstChild.shouldAutorotate
    }

    override public var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        guard let firstChild = children.first else {
            return super.supportedInterfaceOrientations
        }
        return firstChild.supportedInterfaceOrientations
    }

    override public var prefersStatusBarHidden: Bool {
        guard let firstChild = children.first else {
            return super.prefersStatusBarHidden
        }
        return firstChild.prefersStatusBarHidden
    }

    override public func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        hideNavigationBar()
    }

    override public func pushViewController(_ viewController: UIViewController, animated: Bool) {
        guard topViewController != viewController else {
            // There have been issues with this, and if we don't catch it like this it will crash
            // the app.
            // The root cause is that models are rapidly being emitted from KMP into
            // [AppUiStateMachineManagerImpl]
            // before we have a chance to fully push them on the screen.

            // UINavigationController doesn't handle rapid pushes to the stack very well.
            // Example: nav.push(controllerA) followed rapidly by nav.push(controllerA) winds up
            // calling nav.push(controllerA) again internally, causing this crash.

            // Fix this by consolidating multiple separate loading states to a single one,
            // emitting loading states of the same ID to prevent duplication, or adding a minimum
            // time to the loading state.

            assertionFailure(
                "Attempting to push view controller of same instance. See comments above assertion."
            )
            log(.warn) { "Attempting to push view controller of same instance: \(viewController)" }
            return
        }

        super.pushViewController(viewController, animated: animated)
    }

    // MARK: - UIGestureRecognizerDelegate

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard supportsBackSwipeGesture else {
            return false
        }

        guard gestureRecognizer == interactivePopGestureRecognizer else {
            fatalError(
                "We should only be the delegate of the interactivePopGestureRecognizer, not \(gestureRecognizer.debugDescription)"
            )
        }

        guard viewControllers.count > 1 else {
            // There is no view controller above the current view controller, we are at the root
            // Don't allow the swipe to go back gesture
            return false
        }

        return true
    }

    // MARK: - Private Methods

    private func hideNavigationBar() {
        isNavigationBarHidden = true
        interactivePopGestureRecognizer?.delegate = self
    }

}

// MARK: - Completion Callbacks

public extension UINavigationController {
    /// Helper method to trigger the completion callback right after a navigation transition ends
    private func completionHelper(for completion: (() -> Void)?) {
        if let transitionCoordinator = self.transitionCoordinator {
            transitionCoordinator.animate(alongsideTransition: nil) { _ in
                completion?()
            }
        } else {
            completion?()
        }
    }

    /// `pushViewController` method with completion callback
    func pushViewController(
        _ viewController: UIViewController,
        animated: Bool,
        completion: (() -> Void)?
    ) {
        self.pushViewController(viewController, animated: animated)
        self.completionHelper(for: completion)
    }

    /// `popViewController` method with completion callback
    func popViewController(animated: Bool, completion: (() -> Void)?) {
        self.popViewController(animated: animated)
        self.completionHelper(for: completion)
    }

    /// `popToRootViewController` method with completion callback
    func popToRootViewController(animated: Bool, completion: (() -> Void)?) {
        self.popToRootViewController(animated: animated)
        self.completionHelper(for: completion)
    }

    /// `popToViewController` method with completion callback
    func popToViewController(
        _ viewController: UIViewController,
        animated: Bool,
        completion: (() -> Void)?
    ) -> [UIViewController]? {
        let viewControllers = self.popToViewController(viewController, animated: animated)
        self.completionHelper(for: completion)
        return viewControllers
    }
}
