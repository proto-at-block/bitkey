import UIKit

// MARK: -

public struct QRCodeScannerViewControllerModel {

    public struct Reticle {
        public var borderColor: UIColor
        public var borderWidth: CGFloat
        public var descriptionLabel: UILabelModel?

        public static func standard(descriptionLabel: UILabelModel?) -> Reticle {
            return .init(
                borderColor: .white,
                borderWidth: 2,
                descriptionLabel: descriptionLabel
            )
        }
    }

    public var headerModel: HeaderView<ModeledButton, UILabel, ModeledButton>.Model

    /// The focus area for the scanner view
    public var reticle: Reticle

    /// The color of the overlay applied to the preview layer everywhere
    /// but the reticle view (to add emphasis to the reticle view)
    public var overlayBackgroundColor: UIColor
    
    public var primaryButtonModel: UIButtonModel?
    public var secondaryButtonModel: UIButtonModel?

    public init(
        headerModel: HeaderView<ModeledButton, UILabel, ModeledButton>.Model,
        primaryButtonModel: UIButtonModel?,
        secondaryButtonModel: UIButtonModel?,
        reticle: Reticle,
        overlayBackgroundColor: UIColor = .black.withAlphaComponent(0.6)
    ) {
        self.headerModel = headerModel
        self.primaryButtonModel = primaryButtonModel
        self.secondaryButtonModel = secondaryButtonModel
        self.reticle = reticle
        self.overlayBackgroundColor = overlayBackgroundColor
    }

}
