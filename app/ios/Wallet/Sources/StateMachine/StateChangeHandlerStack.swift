import UIKit

/**
 * An object that manages a stack of `StateChangeHandler` objects for the purpose of presenting
 * a new flow / navigation controller in a child context on top of another flow / state change context.
 *
 * Currently only supports two levels
 */
class StateChangeHandlerStack: NSObject, UINavigationControllerDelegate {

    // MARK: - Public Properties

    /// The very top of the stack, what is showing.
    var topScreenModelKey: String {
        return topStateChangeHandler.currentScreenModelKey ?? ""
    }

    var topViewController: UIViewController? {
        return topStateChangeHandler.currentViewController
    }

    var topPresentedViewController: UIViewController? {
        return topViewController?.presentedViewController
    }

    /// Whether or not a child flow is being presented
    var isPresentingFlowOnRoot: Bool {
        return presentedStateChangeHandler != nil
    }

    /// The very bottom of the stack
    var rootViewController: UIViewController? {
        return rootStateChangeHandler.currentViewController
    }

    // MARK: - Private Properties

    /// The very top of the stack, what is showing.
    private var topStateChangeHandler: StateChangeHandler {
        return presentedStateChangeHandler
            ?? rootStateChangeHandler
    }

    /// The flow presented on top of the root, if any
    private var presentedStateChangeHandler: StateChangeHandler?

    /// The very bottom of the stack
    private var rootStateChangeHandler: StateChangeHandler

    // MARK: - Life Cycle

    init(rootStateChangeHandler: StateChangeHandler) {
        self.rootStateChangeHandler = rootStateChangeHandler

        super.init()
    }

    // MARK: - Public Methods

    func present(stateChangeHandler: StateChangeHandler) {
        // Before we try to present, check if something is already presented in which
        // case there'd be an error because we only support 2 levels right now
        guard !isPresentingFlowOnRoot else {
            log(.error) {
                "Attempted to present a 3rd level (present on top of a presented view controller) for key \(stateChangeHandler.currentScreenModelKey ?? "null")"
            }
            return
        }

        // Make sure to set the `presentedStateChangeHandler` first so that
        // `AppUiStateMachineManagerImpl` has access
        // to it (`AppUiStateMachineManagerImpl` accesses the presented view controller through it)
        // if other models are
        // emitted from the state machine while the presentation animation is happening
        presentedStateChangeHandler = stateChangeHandler
        rootStateChangeHandler.navViewController.present(
            stateChangeHandler.navViewController,
            animated: true,
            completion: .none
        )
    }

    func dismissPresentedStateChangeHandler() {
        // Make sure to clear the `presentedStateChangeHandler` first so that
        // `AppUiStateMachineManagerImpl`
        // doesn't think something is still being presented if other models are emitted from the
        // state
        // machine while the dismissal animation is happening
        presentedStateChangeHandler = nil
        rootStateChangeHandler.navViewController.dismiss(animated: true, completion: .none)
    }

    public func pushOrPopTo(
        vc: UIViewController,
        forStateKey stateKey: String,
        animation: StateChangeHandler.AnimationStyle?
    ) {
        topStateChangeHandler.pushOrPopTo(vc: vc, forStateKey: stateKey, animation: animation)
    }

    public func clearBackStack() {
        topStateChangeHandler.clearBackStack()
    }

}
