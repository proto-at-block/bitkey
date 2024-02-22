import Combine
import Shared
import UIKit

/**
 * An object that handles observing the model flow for the shared `AppUiStateMachine`, mapping its output to native view
 * models, and showing the screens via the `StateChangeHandler`.
 *
 * Consumers should initialize this manager and hook up to the UI it outputs via `appViewController`
 */
public protocol AppUiStateMachineManager {

    var appViewController: UINavigationController { get }

    func connectSharedStateMachine()

}
