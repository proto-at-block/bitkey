import Combine
import KMPNativeCoroutinesCombine
import Lottie
import PhotosUI
import SafariServices
import Shared
import SwiftUI
import UIKit

// MARK: -

class BiometricPromptUiStateMachineManager {
    // MARK: - Private Types

    /// An action that needs to be taken as a result of a new screen model emission from the KMP
    /// state machine
    private enum ScreenModelPresentationAction {
        /// No presentation action needs to be taken. An optional action that runs after bottom
        /// sheet is processed can be provided.
        case none(doLast: (() -> Void)? = nil)
        /// A new view controller needs to be shown, either via present, push, or pop
        case showNewView(
            vc: UIViewController,
            key: String,
            animation: StateChangeHandler.AnimationStyle?
        )

        static var none: Self {
            return .none()
        }
    }

    // MARK: - Public Properties

    /// The view controller for the entire app
    public let appViewController: UINavigationController

    // MARK: - Private Properties

    private let biometricPromptUiStateMachine: BiometricPromptUiStateMachineImpl
    private var cancellablesBag = Set<AnyCancellable>()
    private let stateChangeHandlerStack: StateChangeHandlerStack

    // MARK: - Life Cycle

    public init(
        biometricPromptUiStateMachine: BiometricPromptUiStateMachineImpl,
        appViewController: UINavigationController
    ) {
        self.biometricPromptUiStateMachine = biometricPromptUiStateMachine
        self.appViewController = appViewController
        self.stateChangeHandlerStack = .init(
            rootStateChangeHandler: .init(navController: appViewController)
        )
    }

    // MARK: - Public Methods

    public func connectSharedStateMachine() {
        // Convert our KMP model flows to a Combine publisher
        createPublisher(for: StateMachineNativeKt.modelFlow(
            biometricPromptUiStateMachine as! StateMachine,
            props: ()
        ))
        .receive(on: RunLoop.main)
        .map { $0 as? ScreenModel }
        .sink(
            receiveCompletion: handleStateMachineCompletion,
            receiveValue: handleStateMachineOutput
        )
        .store(in: &cancellablesBag)
    }

    public func disconnectStateMachine() {
        cancellablesBag.forEach { $0.cancel() }
    }

    // MARK: - Private Methods

    private func handleStateMachineCompletion(completion: Subscribers.Completion<Error>) {
        switch completion {
        case let .failure(error):
            // This will fire when the state machine encounters an error.
            fatalError("A KMP state machine sent completion unexpectedly: \(error).")
        case .finished:
            // We don't _think_ the state machine will ever complete without an error,
            // but let's find out if it does!
            fatalError("A KMP state machine sent a `finished` completion event unexpectedly")
        }
    }

    private func handleStateMachineOutput(model: ScreenModel?) {
        guard let model else {
            // if we receive a null model and the App Switcher Window is shown, we will hide it
            if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
               let window = windowScene.windows.first(where: { $0 is AppSwitcherWindow })
            {
                window.isHidden = true
                window.windowScene = nil
            }

            return
        }

        // First, dismiss any presented view controllers as necessary
        // We need to dismiss if the next screen model we get has a `root` presentation, but
        // the current state has a view controller presented on top of root.
        if model.presentationStyle == .root || model.presentationStyle == .rootfullscreen,
           stateChangeHandlerStack.isPresentingFlowOnRoot
        {
            stateChangeHandlerStack.dismissPresentedStateChangeHandler()
        }

        // Then, try to either create a new view controller for the body model or
        // just apply the model to the current view controller
        let action = apply(screenModel: model)

        if !(model.body is FwupNfcBodyModel) {
            FwupNfcMaskOverlayViewController.hide()
        }

        switch action {
        case let .showNewView(viewController, key, viewAnimation):
            // We have a new view controller to show
            // First check if a bottom sheet is presented on the current view controller and dismiss
            // if so
            if let bottomSheet = stateChangeHandlerStack
                .topPresentedViewController as? BottomSheetViewController
            {
                bottomSheet.dismiss(animated: true)
            }

            // Always animate as a fade from the splash screen
            let animation: StateChangeHandler.AnimationStyle? =
                stateChangeHandlerStack.topScreenModelKey
                    .contains("ios-splash") ? .fade : viewAnimation

            // Present the new view controller using the style given to us from the state machine
            switch model.presentationStyle {
            case .root, .fullscreen, .rootfullscreen,
                 .modal where stateChangeHandlerStack.isPresentingFlowOnRoot,
                 .modalfullscreen where stateChangeHandlerStack.isPresentingFlowOnRoot:
                stateChangeHandlerStack.pushOrPopTo(
                    vc: viewController,
                    forStateKey: key,
                    animation: animation
                )

            default:
                fatalError("Presentation Style not defined")
            }
        case .none:
            // no-op
            break
        }
    }

    /**
     * Applies the given model to either a new view controller instance or the current view controller showing, as appropriate.
     * Returns the presentation action needed for the model, based on if a new view controller needed to be created
     */
    private func apply(
        screenModel: ScreenModel
    ) -> ScreenModelPresentationAction {
        let topViewController = stateChangeHandlerStack.topViewController
        let rootViewController = stateChangeHandlerStack.rootViewController

        let bodyModel = screenModel.body
        switch bodyModel {
        case is NfcBodyModel:
            // KMP should be handling the NFCManager so there is nothing to do here
            // We might eventually show different backgrounds for NFC
            return .none

        case _ as FwupNfcBodyModel:
            FwupNfcMaskOverlayViewController.show()
            return .none

        case let viewModel as SplashBodyModel:
            if topViewController is UIHostingController<SplashScreenView> {
                return .none
            } else {
                return .showNewView(
                    vc: UIHostingController(rootView: SplashScreenView(viewModel: viewModel)),
                    key: "ios-splash",
                    animation: .none
                )
            }

        case let viewModel as SplashLockModel:
            if let vc = rootViewController as? SwiftUIWrapperViewController<SplashLockScreenView> {
                vc.updateWrappedView(
                    SplashLockScreenView(viewModel: viewModel),
                    screenModel: screenModel
                )
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(
                    SplashLockScreenView(viewModel: viewModel),
                    screenModel: screenModel
                )
                return .showNewView(vc: vc, key: bodyModel.key, animation: .none)
            }

        default:
            fatalError("Unhandled model body: \(bodyModel)")
        }
    }

}
