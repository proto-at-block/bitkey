import Combine
import KMPNativeCoroutinesCombine
import Shared
import SwiftUI
import UIKit
import SafariServices
import PhotosUI

// MARK: -

public class AppUiStateMachineManagerImpl: AppUiStateMachineManager {

    // MARK: - Public Types

    public struct Context {
        let qrCodeScannerViewControllerFactory: QRCodeScannerViewControllerFactory

        public init(
            qrCodeScannerViewControllerFactory: QRCodeScannerViewControllerFactory
        ) {
            self.qrCodeScannerViewControllerFactory = qrCodeScannerViewControllerFactory
        }
    }

    // MARK: - Private Types

    /// An action that needs to be taken as a result of a new screen model emission from the KMP state machine
    private enum ScreenModelPresentationAction {
        /// No presentation action needs to be taken. An optional action that runs after bottom sheet is processed can be provided.
        case none(doLast: (() -> Void)? = nil)
        /// A new view controller needs to be shown, either via present, push, or pop
        case showNewView(vc: UIViewController, key: String, animation: StateChangeHandler.AnimationStyle?)

        static var none: Self {
            return .none()
        }
    }

    // MARK: - Public Properties

    /// The view controller for the entire app
    public let appViewController: UINavigationController

    // MARK: - Private Properties

    private let appUiStateMachine: AppUiStateMachine
    private var cancellablesBag = Set<AnyCancellable>()
    private let context: Context
    private let stateChangeHandlerStack: StateChangeHandlerStack

    // MARK: - Life Cycle

    public init(
        appUiStateMachine: AppUiStateMachine,
        appViewController: UINavigationController,
        context: Context
    ) {
        self.appUiStateMachine = appUiStateMachine
        self.appViewController = appViewController
        self.context = context
        self.stateChangeHandlerStack = .init(
            rootStateChangeHandler: .init(navController: appViewController)
        )
    }

    // MARK: - Public Methods

    public func connectSharedStateMachine() {
        // Convert our KMP model flows to a Combine publisher
        createPublisher(for: appUiStateMachine.modelFlowNative(props: ()))
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

    private func handleStateMachineCompletion(completion: Subscribers.Completion<Error>){
            switch completion {
            case .failure(let error):
                // This will fire when the state machine encounters an error.
                fatalError("A KMP state machine sent completion unexpectedly: \(error).")
            case .finished:
                // We don't _think_ the state machine will ever complete without an error,
                // but let's find out if it does!
                fatalError("A KMP state machine sent a `finished` completion event unexpectedly")
            }
    }

    private func handleStateMachineOutput(model: ScreenModel?) {
        guard let model = model else {
            log(.error) { "Unexpected null ScreenModel" }
            return
        }

        // First, dismiss any presented view controllers as necessary
        // We need to dismiss if the next screen model we get has a `root` presentation, but
        // the current state has a view controller presented on top of root.
        if (model.presentationStyle == .root || model.presentationStyle == .rootfullscreen),
            stateChangeHandlerStack.isPresentingFlowOnRoot {
            stateChangeHandlerStack.dismissPresentedStateChangeHandler()
        }

        // Then, try to either create a new view controller for the body model or
        // just apply the model to the current view controller
         let action = apply(screenModel: model)

        if !(model.body is FwupNfcBodyModel) {
            FwupNfcMaskOverlayViewController.hide()
        }

        switch action {
        case .none(let doLast):
            handleSystemUIModelUpdate(model: model.systemUIModel)
            
            // TODO (W-3706): Do this in SwiftUI when we convert to pure SwiftUI
            // No new view to show, just check for bottom sheets to present or dismiss from the updated model
            if let bottomSheetModel = model.bottomSheetModel {
                if let bottomSheet = stateChangeHandlerStack.topViewController?.presentedViewController as? BottomSheetViewController {
                    bottomSheet.update(viewModel: bottomSheetModel)
                } else {
                    let bottomSheet = BottomSheetViewController(viewModel: bottomSheetModel)
                    stateChangeHandlerStack.topViewController?.present(bottomSheet, animated: true)
                }
            } else if let bottomSheet = stateChangeHandlerStack.topViewController?.presentedViewController as? BottomSheetViewController {
                bottomSheet.dismiss(animated: true)
            }

            // This is a workaround for (W-5874) until we have time to fix it properly. We need an action that runs after bottom sheet has been closed.
            doLast?()

        case let .showNewView(viewController, key, viewAnimation):
            // We have a new view controller to show
            // First check if a bottom sheet is presented on the current view controller and dismiss if so
            if let bottomSheet = stateChangeHandlerStack.topPresentedViewController as? BottomSheetViewController {
                bottomSheet.dismiss(animated: true)
            }
            
            dismissSystemUIModelIfNeeded()

            // Always animate as a fade from the splash screen
            let animation: StateChangeHandler.AnimationStyle? =
                stateChangeHandlerStack.topScreenModelKey.contains("ios-splash") ? .fade : viewAnimation

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
                
            case .modal:
                // Only allow swipe to dismiss if the view shows a close button
                stateChangeHandlerStack.present(
                    stateChangeHandler: .init(
                        rootViewController: (vc: viewController, key: key),
                        presentationStyle: .modal(swipeToDismissCallback: model.body.swipeToDismissCallback)
                    )
                )
                
            case .modalfullscreen:
                stateChangeHandlerStack.present(
                    stateChangeHandler: .init(
                        rootViewController: (vc: viewController, key: key),
                        presentationStyle: .fullScreen
                    )
                )
                
            default:
                fatalError("Presentation Style not defined")
            }
            
            if let systemUIModel = model.systemUIModel {
                presentSystemUIModel(model: systemUIModel)
            }
            
            // TODO (W-3706): Do this in SwiftUI when we convert to pure SwiftUI
            // Check for new bottom sheets to present from the new model
            else if let bottomSheetModel = model.bottomSheetModel {
                let bottomSheet = BottomSheetViewController(viewModel: bottomSheetModel)
                stateChangeHandlerStack.topViewController?.present(bottomSheet, animated: true)
            }
        }

        // See if we need to keep the screen on (or reset `isIdleTimerDisabled`)
        switch model.body {
        case let viewModel as FormBodyModel:
            UIApplication.shared.isIdleTimerDisabled = viewModel.keepScreenOn
        default:
            UIApplication.shared.isIdleTimerDisabled = false
        }

        // TODO (W-3706): Do this in SwiftUI when we convert to pure SwiftUI
        // Next, see if there's an alert to present (only if we're not already presenting one)
        let presentedAlert = stateChangeHandlerStack.topPresentedViewController as? UIAlertController
        if let alertModel = model.alertModel, presentedAlert == nil {
            let alert = UIAlertController(model: .init(alertModel: alertModel))
            stateChangeHandlerStack.topViewController?.present(alert, animated: true)
        }

        // See if we need to clear the back stack
        switch model.body {
        case _ as MoneyHomeBodyModel:
            // We clear the nav back stack when we reach Money Home so that view controllers from Onboarding
            // don't get popped back to if we re-use them post Onboarding.
            stateChangeHandlerStack.clearBackStack()
        default:
            break
        }

        // Finally, add or update a gesture recognizer to the top view controller if necessary
        if let onTwoFingerDoubleTap = model.onTwoFingerDoubleTap, let topView = stateChangeHandlerStack.topViewController?.view {
            if let gestureRecognizers = topView.gestureRecognizers,
               let twoFingerDoubleTapGestureRecognizer = gestureRecognizers.compactMap({ $0 as? TwoFingerDoubleTapTapGestureRecognizer }).first {
                // If the current view already has the gesture recognizer, just update the action with the latest from the model
                twoFingerDoubleTapGestureRecognizer.action = onTwoFingerDoubleTap
            } else {
                // Otherwise, create a new gesture recognizer and add to the top view
                let twoFingerDoubleTapGestureRecognizer = TwoFingerDoubleTapTapGestureRecognizer(onTwoFingerDoubleTap)
                topView.addGestureRecognizer(twoFingerDoubleTapGestureRecognizer)
            }
        }
        
        if let onTwoFingerTripleTap = model.onTwoFingerTripleTap, let topView = stateChangeHandlerStack.topViewController?.view {
            if let gestureRecognizers = topView.gestureRecognizers,
               let twoFingerTripleTapGestureRecognizer = gestureRecognizers.compactMap({ $0 as? TwoFingerTripleTapTapGestureRecognizer }).first {
                // If the current view already has the gesture recognizer, just update the action with the latest from the model
                twoFingerTripleTapGestureRecognizer.action = onTwoFingerTripleTap
            } else {
                // Otherwise, create a new gesture recognizer and add to the top view
                let twoFingerTripleTapGestureRecognizer = TwoFingerTripleTapTapGestureRecognizer(onTwoFingerTripleTap)
                topView.addGestureRecognizer(twoFingerTripleTapGestureRecognizer)
            }
        }
    }
    
    private func handleSystemUIModelUpdate(model: SystemUIModel?) {
        if let model {
            switch model {
            case let mediaPickerModel as SystemUIModelMediaPickerModel:
                if let existingPicker = stateChangeHandlerStack.topViewController?.presentedViewController as? PHPickerViewController {
                    // TODO: On iOS 17 we can update configuration of the picker
                    if existingPicker.delegate !== mediaPickerModel {
                        existingPicker.dismiss(animated: false)
                        let newPicker = PHPickerViewController(model: mediaPickerModel)
                        stateChangeHandlerStack.topViewController?.present(newPicker, animated: false)
                    }
                } else {
                    let newPicker = PHPickerViewController(model: mediaPickerModel)
                    stateChangeHandlerStack.topViewController?.present(newPicker, animated: true)
                }
            default:
                fatalError("Implementation of SystemUIModel \(model) not handled!")
            }
        } else {
            dismissSystemUIModelIfNeeded()
        }
    }
    
    private func dismissSystemUIModelIfNeeded() {
        if let existingPicker = stateChangeHandlerStack.topViewController?.presentedViewController as? PHPickerViewController {
            existingPicker.dismiss(animated: true)
        }
    }
    
    private func presentSystemUIModel(model: SystemUIModel) {
        switch model {
        case let mediaPickerModel as SystemUIModelMediaPickerModel:
            let controller = PHPickerViewController(model: mediaPickerModel)
            stateChangeHandlerStack.topViewController?.present(controller, animated: true)
        default:
            fatalError("Implementation of SystemUIModel \(model) not handled!")
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

        case let viewModel as FormBodyModel:
            let shouldBeSameView = bodyModel.shouldBeSameView(currentKey: stateChangeHandlerStack.topScreenModelKey)
            if let vc = topViewController as? SwiftUIWrapperViewController<FormView>, shouldBeSameView {
                vc.updateWrappedView(FormView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(FormView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: bodyModel.key, animation: bodyModel.animatePushOrPop(currentKey: stateChangeHandlerStack.topScreenModelKey) ? .pushPop : .none)
            }

        case let viewModel as PairNewHardwareBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<PairNewHardwareView> {
                vc.updateWrappedView(PairNewHardwareView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(PairNewHardwareView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: bodyModel.key, animation: .pushPop)
            }

        case let viewModel as AddressQrCodeBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<AddressQrCodeView> {
                vc.updateWrappedView(AddressQrCodeView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(AddressQrCodeView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-receive", animation: .none)
            }

        case let viewModel as AppDelayNotifyInProgressBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<AppDelayNotifyInProgressView> {
                vc.updateWrappedView(AppDelayNotifyInProgressView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(AppDelayNotifyInProgressView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-app-delay-notify-in-progress", animation: .pushPop)
            }

        case let viewModel as QrCodeScanBodyModel:
            if let vc = topViewController as? QRCodeScannerViewController {
                vc.apply(model: viewModel.nativeModel)
                return .none
            } else {
                let vc = context.qrCodeScannerViewControllerFactory.make(
                    onClose: viewModel.onClose,
                    onQrCodeScanned: viewModel.onQrCodeScanned
                )
                vc.apply(model: viewModel.nativeModel)
                return .showNewView(vc: vc, key: "ios-qr-scan", animation: .none)
            }

        case let viewModel as FwupInstructionsBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<FwupInstructionsView> {
                vc.updateWrappedView(FwupInstructionsView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(FwupInstructionsView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-fwup-instructions", animation: .pushPop)
            }

        case let viewModel as BitkeyGetStartedModel:
            if let vc = rootViewController as? SwiftUIWrapperViewController<BitkeyGetStartedView> {
                vc.updateWrappedView(BitkeyGetStartedView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(BitkeyGetStartedView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-bitkey-get-started", animation: .pushPop)
            }

        case let viewModel as ChooseAccountAccessModel:
            if let vc = rootViewController as? SwiftUIWrapperViewController<ChooseAccountAccessView> {
                vc.updateWrappedView(ChooseAccountAccessView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(ChooseAccountAccessView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-account-access", animation: .pushPop)
            }

        case let viewModel as DebugMenuBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<DebugMenuView> {
                vc.updateWrappedView(DebugMenuView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(DebugMenuView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-debug", animation: .pushPop)
            }
            
        case let viewModel as FeatureFlagsBodyModel:
            if let vc = topViewController as? FeatureFlagsViewController {
                vc.apply(model: viewModel)
                return .none
            } else {
                let vc = FeatureFlagsViewController(viewModel: viewModel)
                return .showNewView(vc: vc, key: "ios-debug-feature-flags", animation: .pushPop)
            }

        case let viewModel as FirmwareMetadataBodyModel:
            if let vc = topViewController as? FirmwareMetadataViewController {
                vc.apply(model: viewModel)
                return .none
            } else {
                let vc = FirmwareMetadataViewController(viewModel: viewModel)
                return .showNewView(vc: vc, key: "ios-debug-fm-metadata", animation: .pushPop)
            }

        case let viewModel as LoadingBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<LoadingView> {
                vc.updateWrappedView(LoadingView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(LoadingView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: viewModel.id?.name ?? "ios-loading", animation: .pushPop)
            }

        case let viewModel as LogsBodyModel:
            if let vc = topViewController as? LogsViewController {
                vc.apply(model: viewModel)
                return .none
            } else {
                let vc = LogsViewController(viewModel: viewModel)
                return .showNewView(vc: vc, key: "ios-debug-logs", animation: .pushPop)
            }

        case let viewModel as MobilePayStatusModel:
            if let vc = rootViewController as? SwiftUIWrapperViewController<MobileTransactionsView> {
                vc.updateWrappedView(MobileTransactionsView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(MobileTransactionsView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-quick-pay", animation: .pushPop)
            }

        case let viewModel as MoneyHomeBodyModel:
            if let vc = rootViewController as? SwiftUIWrapperViewController<MoneyHomeView> {
                vc.updateWrappedView(MoneyHomeView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(MoneyHomeView(viewModel: viewModel), screenModel: screenModel)
                // Don't animate from onboarding
                let screensToNotAnimateFrom = ["NEW_ACCOUNT_SERVER_KEYS_LOADING", "ios-success"]
                let shouldAnimate = !screensToNotAnimateFrom.contains { stateChangeHandlerStack.topScreenModelKey.contains($0) }
                return .showNewView(vc: vc, key: "ios-money-home", animation: shouldAnimate ? .pushPop : .none)
            }

        case let viewModel as SettingsBodyModel:
            if let vc = rootViewController as? SwiftUIWrapperViewController<SettingsView> {
                vc.updateWrappedView(SettingsView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(SettingsView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-settings", animation: .pushPop)
            }

        case let viewModel as SpendingLimitPickerModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<SpendingLimitPickerView> {
                vc.updateWrappedView(SpendingLimitPickerView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(SpendingLimitPickerView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-limit-picker", animation: .pushPop)
            }

        case let viewModel as SuccessBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<SuccessView> {
                vc.updateWrappedView(SuccessView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(SuccessView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-success", animation: .none)
            }

        case let viewModel as TransferAmountBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<TransferAmountView> {
                vc.updateWrappedView(TransferAmountView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(TransferAmountView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-transfer-amount", animation: .pushPop)
            }
            
        case let viewModel as CustomElectrumServerBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<ElectrumServerSettingsView> {
                vc.updateWrappedView(ElectrumServerSettingsView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(ElectrumServerSettingsView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-electrum-server", animation: .pushPop)
            }
        
        case let viewModel as InAppBrowserModel:
            return .none {
                viewModel.open()
            }

        case let viewModel as AnalyticsBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<AnalyticsView> {
                vc.updateWrappedView(AnalyticsView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(AnalyticsView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-analytics", animation: .pushPop)
            }
        case let viewModel as EducationBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<EducationView> {
                vc.updateWrappedView(EducationView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(EducationView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-education", animation: .pushPop)
            }
        case let viewModel as CustomAmountBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<CustomAmountView> {
                vc.updateWrappedView(CustomAmountView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(CustomAmountView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-custom-purchase-amount", animation: .pushPop)
            }
        
        case let viewModel as CloudBackupHealthDashboardBodyModel:
            if let vc = topViewController as? SwiftUIWrapperViewController<CloudBackupHealthDashboardView> {
                vc.updateWrappedView(CloudBackupHealthDashboardView(viewModel: viewModel), screenModel: screenModel)
                return .none
            } else {
                let vc = SwiftUIWrapperViewController(CloudBackupHealthDashboardView(viewModel: viewModel), screenModel: screenModel)
                return .showNewView(vc: vc, key: "ios-cloud-backup-health-dashboard", animation: .pushPop)
            }

        default:
            fatalError("Unhandled model body: \(bodyModel)")
        }
    }

}

extension SystemUIModelMediaPickerModel: PHPickerViewControllerDelegate {
    public func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        onMediaPicked(results.compactMap { result in
            media(from: result)
        })
    }
    
    private func media(from result: PHPickerResult) -> Media? {
        let itemProvider = result.itemProvider
        let supportedTypes: [UTType] = [
            .image,
            .video,
        ]
        
        guard let mediaType = supportedTypes.first(where: {
            itemProvider.hasItemConformingToTypeIdentifier($0.identifier)
        }) else {
            // TODO: We should inform the user that we'll skip this media and why.
            return nil
        }
        
        let conformingRegisteredContentTypes = if #available(iOS 16.0, *) {
            itemProvider.registeredContentTypes(conformingTo: mediaType)
        } else {
            itemProvider.registeredTypeIdentifiers.compactMap { UTType($0) }.filter { $0.conforms(to: mediaType) }
        }
        
        guard let mimeType = conformingRegisteredContentTypes.compactMap({ $0.preferredMIMEType }).first else {
            // TODO: We should inform the user that we'll skip this media and why.
            return nil
        }
        let fileExtension = conformingRegisteredContentTypes.compactMap { $0.preferredFilenameExtension }.first.map { ".\($0)" } ?? ""
        
        return MediaExtKt.nativeMedia(
            name: (itemProvider.suggestedName ?? "unknown") + fileExtension,
            mimeType: MimeType(name: mimeType),
            loadUrl: { callback in
                itemProvider.loadFileRepresentation(forTypeIdentifier: mediaType.identifier) { url, error in
                    if let url {
                        _ = callback(url, nil)
                    } else {
                        _ = callback(nil, error)
                    }
                }
            }
        )
    }
}

extension PHPickerViewController {
    convenience init(model: SystemUIModelMediaPickerModel) {
        var configuration = PHPickerConfiguration()
        configuration.filter = PHPickerFilter.any(of: [
            PHPickerFilter.images,
            PHPickerFilter.screenshots,
            PHPickerFilter.videos,
            PHPickerFilter.screenRecordings,
        ])
        configuration.preferredAssetRepresentationMode = .current
        self.init(configuration: configuration)
        delegate = model
    }
}

// MARK: -

private class TwoFingerDoubleTapTapGestureRecognizer: UITapGestureRecognizer {

    // MARK: - Public Properties

    var action: () -> Void

    // MARK: - Life Cycle

    init(_ action: @escaping () -> Void) {
        self.action = action

        super.init(target: nil, action: nil)

        numberOfTapsRequired = 2
        numberOfTouchesRequired = 2
        addTarget(self, action: #selector(handleAction))
    }

    // MARK: - Private Methods

    @objc
    private func handleAction() {
        action()
    }

}


private class TwoFingerTripleTapTapGestureRecognizer: UITapGestureRecognizer {

    // MARK: - Public Properties

    var action: () -> Void

    // MARK: - Life Cycle

    init(_ action: @escaping () -> Void) {
        self.action = action

        super.init(target: nil, action: nil)

        numberOfTapsRequired = 3
        numberOfTouchesRequired = 2
        addTarget(self, action: #selector(handleAction))
    }

    // MARK: - Private Methods

    @objc
    private func handleAction() {
        action()
    }

}

// MARK: -

private extension BodyModel {

    /// Whether we should use the same instance of the underlying view and just call apply(model:) instead of creating a new view
    func shouldBeSameView(currentKey: String) -> Bool {
        // Always use the same view for the same key
        if key == currentKey {
            return true
        }

        // Use the same view if we are going from phone number <> email for smoother transitions
        let isGoingFromPhoneToEmail = currentKey.contains("phone_number_input") && key.contains("email_input")
        let isGoingFromEmailToPhone = currentKey.contains("email_input") && key.contains("phone_number_input")

        return isGoingFromPhoneToEmail || isGoingFromEmailToPhone
    }

    func animatePushOrPop(currentKey: String) -> Bool {
        // Don't animate from the splash screen
        if currentKey.contains("ios-splash") {
            return false
        }

        // Don't animate recovery loading
        if currentKey.contains("checking-for-existing-recovery") {
            return false
        }

        // Don't animate back to back loading screens
        if currentKey.contains("LoadingScreenModel"),
           key.contains("LoadingScreenModel") {
            return false
        }

        // Don't animate transitions between address text entry and QR code scanner
        if (currentKey.contains("BitcoinRecipientAddressScreenModel") && key.contains("ios-qr-scanner"))
            || (key.contains("BitcoinRecipientAddressScreenModel") && currentKey.contains("ios-qr-scanner")) {
            return false
        }

        switch key {
        case "build.wallet.statemachine.core.LoadingScreenModel-app-loading.":
            return false
        default:
            return true
        }
    }

    /// Decide if we can swipe to dismiss this body in modal presentation based on the contents
    /// If the callback returned is non-null, the content will be allowed to be swiped to be dismissed
    var swipeToDismissCallback: (() -> Void)? {
        switch self {
        case let model as AddressQrCodeBodyModel:
            return model.onBack

        case let model as SpendingLimitPickerModel:
            return model.onBack

        case let model as FormBodyModel:
            return model.onSwipeToDismiss

        default:
            return nil
        }
    }

}
