import UIKit

// MARK: -

public enum QRCodeScannerModelFactory {

    // MARK: - Internal Static Methods

    static func makeCameraDeniedAlert(
        actionHandler: @escaping (QRCodeScannerPresenterImpl.InternalAction) -> Void
    ) -> UIAlertController.ButtonModel {
        return .init(
            title: Strings.CameraDeniedAlert.title,
            message: Strings.CameraDeniedAlert.message,
            actions: [
                .cancel(action: { actionHandler(.didCancelCameraDeniedAlert) }),
                .init(
                    title: Strings.CameraDeniedAlert.confirmActionTitle,
                    action: { actionHandler(.didConfirmCameraDeniedAlert) },
                    style: .default
                )
            ]
        )
    }

    static func makeCameraUnavailableAlert(
        actionHandler: @escaping (QRCodeScannerPresenterImpl.InternalAction) -> Void
    ) -> UIAlertController.ButtonModel {
        return .init(
            title: Strings.CameraUnavailableAlert.title,
            message: Strings.CameraUnavailableAlert.message,
            actions: [
                .init(
                    title: Strings.CameraUnavailableAlert.closeActionTitle,
                    action: { actionHandler(.didCancelCameraUnavailableAlert) },
                    style: .default
                )
            ]
        )
    }

}

// MARK: -

private extension QRCodeScannerModelFactory {

    enum Strings {

        enum CameraDeniedAlert {
            static let title = "No Camera access"
                .localized(comment: "Title for for alert that gives the user instructions to enable camera access after they have previously denied access.")

            static let message = "To scan codes, please allow us to use your camera in Settings"
                .localized(comment: "Subtitle for for alert that gives the user instructions to enable camera access after they have previously denied access")

            static let confirmActionTitle = "Settings"
                .localized(comment: "Button title for alert that brings the user to Settings > Bitkey so they can enable photos or camera access.")
        }

        enum CameraUnavailableAlert {
            static let title = "No Camera available"
                .localized(comment: "Title for the alert that informs user about not being able to access their device's camera and use QR Scanner.")

            static let message = "No camera found on the device"
                .localized(comment: "Subtitle for the alert that informs user about not being able to access their device's camera and use QR Scanner.")

            static let closeActionTitle = "Close"
                .localized(comment: "Button title for alert that informs user about not being able to access their device's camera and use QR Scanner.")
        }

    }

}

