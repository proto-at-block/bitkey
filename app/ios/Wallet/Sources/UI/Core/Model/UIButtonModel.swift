import UIKit

// MARK: -

public struct UIButtonModel {

    // MARK: - Public Properties

    public let action: () -> Void
    public let configuration: UIButton.Configuration
    public let isEnabled: Bool

    public let height: CGFloat?
    public let width: CGFloat?

    // MARK: - Internal Properties

    let updateHandler: UIButton.ConfigurationUpdateHandler

    // MARK: - Life Cycle

    /**
     * Creates a model for a button with a title and an option icon
     */
    public init(
        backgroundColor: UIColor,
        highlightedBackgroundColor: UIColor,
        height: CGFloat,
        image: UIImage? = nil,
        imagePlacement: NSDirectionalRectEdge = .leading,
        isEnabled: Bool = true,
        isLoading: Bool = false,
        title: String,
        titleColor: UIColor,
        titleFont: UIFont,
        titleKerning: CGFloat,
        width: CGFloat? = nil,
        isTextOnly: Bool = false,
        action: @escaping () -> Void
    ) {
        // We use UIButton.Configuration to get the ability to easily add a symbol icon
        var configuration = UIButton.Configuration.filled()
        configuration.baseBackgroundColor = backgroundColor
        configuration.baseForegroundColor = titleColor
        configuration.contentInsets = .init(top: 0, leading: 16, bottom: 0, trailing: 16)
        configuration.cornerStyle = .capsule
        if isTextOnly {
            configuration.contentInsets = .zero
        }

        if isLoading {
            configuration.showsActivityIndicator = isLoading
        } else {
            var titleAttributeContainer = AttributeContainer()
            titleAttributeContainer.foregroundColor = titleColor
            titleAttributeContainer.font = titleFont
            titleAttributeContainer.kern = titleKerning
            configuration.attributedTitle = .init(title, attributes: titleAttributeContainer)
            configuration.image = image
            configuration.imagePadding = 4
            configuration.imagePlacement = imagePlacement
            configuration.preferredSymbolConfigurationForImage = UIImage.SymbolConfiguration(
                font: titleFont,
                scale: .small
            )
        }

        self.init(
            backgroundColor: backgroundColor,
            highlightedBackgroundColor: highlightedBackgroundColor,
            configuration: configuration,
            height: height,
            isEnabled: isEnabled,
            width: width,
            action: action
        )
    }

    /**
     * Creates a model for a button with an image instead of a title
     */
    public init(
        backgroundColor: UIColor,
        highlightedBackgroundColor: UIColor,
        cornerRadius: CGFloat = 0,
        foregroundColor: UIColor,
        height: CGFloat? = nil,
        image: UIImage,
        isEnabled: Bool = true,
        isLoading: Bool = false,
        width: CGFloat? = nil,
        action: @escaping () -> Void
    ) {
        var configuration = UIButton.Configuration.filled()
        configuration.baseBackgroundColor = backgroundColor
        configuration.baseForegroundColor = foregroundColor
        configuration.cornerStyle = .capsule

        if isLoading {
            configuration.showsActivityIndicator = isLoading
        } else {
            configuration.image = image
        }

        self.init(
            backgroundColor: backgroundColor,
            highlightedBackgroundColor: highlightedBackgroundColor,
            configuration: configuration,
            height: height,
            isEnabled: isEnabled,
            width: width,
            action: action
        )
    }

    // Internal common init
    private init(
        backgroundColor: UIColor,
        highlightedBackgroundColor: UIColor,
        configuration: UIButton.Configuration,
        height: CGFloat?,
        isEnabled: Bool,
        width: CGFloat? = nil,
        action: @escaping () -> Void
    ) {
        self.action = action
        self.configuration = configuration
        self.height = height
        self.width = width
        self.isEnabled = isEnabled

        self.updateHandler = { button in
            switch button.state {
            case [.selected, .highlighted], .selected, .highlighted:
                button.configuration?.background.backgroundColor = highlightedBackgroundColor
            case .disabled:
                break
            default:
                button.configuration?.background.backgroundColor = backgroundColor
            }
        }
    }

}

// MARK: -

public final class ModeledButton: UIButton {

    private var actionIdentifier: UIAction.Identifier?

    public func apply(model: UIButtonModel) {
        configurationUpdateHandler = model.updateHandler
        configuration = model.configuration
        tintColor = model.configuration.baseForegroundColor
        tintAdjustmentMode = .normal

        if let height = model.height {
            heightAnchor.constraint(equalToConstant: height).isActive = true
        }

        if let width = model.width {
            widthAnchor.constraint(equalToConstant: width).isActive = true
        }

        isEnabled = model.isEnabled

        if let actionIdentifier = actionIdentifier {
            removeAction(identifiedBy: actionIdentifier, for: .touchUpInside)
        }
        let action = UIAction(handler: { _ in model.action() })
        addAction(action,for: .touchUpInside)
        actionIdentifier = action.identifier
    }

}
