import SwiftUI

// MARK: -

public class FwupNfcMaskOverlayViewController: UIHostingController<NfcMaskView> {

    // MARK: - Private Types

    private enum Model {
        /// The model to use for the NFC mask before a tag connection forms
        static let beforeConnection = NfcMaskView.ViewModel(
            title: "Ready to Update",
            subtitle: "Hold device to phone"
        )

        /// The model to use for the NFC mask after a tag connection forms
        static let afterConnection = NfcMaskView.ViewModel(
            title: "Updating...",
            subtitle: "Continue holding to phone"
        )
    }

    // MARK: - Private Static Properties

    private static var currentOverlay: FwupNfcMaskOverlayViewController?

    // MARK: - Public Static Properties

    // Set when the app loads
    public static var mainWindow: UIWindow?

    // MARK: - Private Properties

    private let viewModel: NfcMaskView.ViewModel

    // MARK: - Life Cycle

    init(viewModel: NfcMaskView.ViewModel) {
        self.viewModel = viewModel
        super.init(rootView: NfcMaskView(viewModel: viewModel))
    }

    @available(*, unavailable)
    @MainActor dynamic required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    // MARK: - Public Static Methods

    public static func show() {
        guard currentOverlay == nil, let currentTopController = topViewController() else {
            return
        }

        let viewController = FwupNfcMaskOverlayViewController(viewModel: Model.beforeConnection)
        viewController.modalTransitionStyle = .crossDissolve
        viewController.modalPresentationStyle = .overFullScreen
        currentOverlay = viewController

        currentTopController.present(viewController, animated: true)
    }

    public static func updateViewModelToAfterConnection() {
        currentOverlay?.viewModel.update(from: Model.afterConnection)
    }

    public static func hide() {
        if let viewController = currentOverlay {
            viewController.dismiss(animated: true)
            currentOverlay = nil
        }
    }

    // MARK: - Private Static Methods

    private static func topViewController() -> UIViewController? {
        guard var topController = mainWindow?.rootViewController else {
            return nil
        }

        while let presentedViewController = topController.presentedViewController {
            topController = presentedViewController
        }
        return topController
    }

    // MARK: - UIViewController

    override public var prefersStatusBarHidden: Bool {
        return true
    }

}
