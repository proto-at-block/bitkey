import Shared
import UIKit

extension QrCodeScanBodyModel {

    var nativeModel: QRCodeScannerViewControllerModel {
        return .init(
            headerModel: .init(
                leadingModel: .header(
                    .smallIconX,
                    backgroundColor: .black.withAlphaComponent(0.25),
                    foregroundColor: .white,
                    action: onClose
                ),
                middleModel: {
                    if let title = headline {
                        return .standard(
                            title,
                            font: .title2,
                            textAlignment: .center,
                            textColor: .white
                        )
                    } else {
                        return nil
                    }
                }()
            ),
            primaryButtonModel: {
                if let button = primaryButton {
                    return .makeButton(
                        backgroundColor: UIColor(button.backgroundColor),
                        title: button.text,
                        titleColor: UIColor(button.titleColor),
                        icon: button.leadingIcon?.uiImage,
                        action: button.onClick.invoke
                    )
                } else {
                    return nil
                }
            }(),
            secondaryButtonModel: {
                if let button = secondaryButton {
                    return .makeButton(
                        backgroundColor: UIColor(button.backgroundColor),
                        title: button.text,
                        titleColor: UIColor(button.titleColor),
                        icon: button.leadingIcon?.uiImage,
                        action: button.onClick.invoke
                    )
                } else {
                    return nil
                }
            }(),
            reticle: .standard(
                descriptionLabel: {
                    if let label = reticleLabel {
                        return .standard(
                            label,
                            font: .body2Bold,
                            textColor: .white
                        )
                    } else {
                        return nil
                    }
                }()
            )
        )
    }

}

// MARK: -

public extension UIButtonModel {

    // MARK: - Image Based

    /// Returns an image-based button inset in a circle
    static func header(
        _ image: UIImage,
        backgroundColor: UIColor = .secondary,
        foregroundColor: UIColor = .secondaryForeground,
        action: @escaping () -> Void
    ) -> UIButtonModel {
        let imageSize = 32.f
        return .init(
            backgroundColor: backgroundColor,
            highlightedBackgroundColor: backgroundColor.highlightedColor,
            cornerRadius: imageSize,
            foregroundColor: foregroundColor,
            height: imageSize,
            image: image,
            width: imageSize,
            action: action
        )
    }
}
