import UIKit

// MARK: -

public struct UITextFieldModel {

    // MARK: - Public Properties

    public let backgroundColor: UIColor
    public let cornerRadius: CGFloat
    public let enableAutoCorrect: Bool
    public let enableWordAutoCapitalization: Bool
    public let height: CGFloat
    public let isSecureTextEntry: Bool
    public let keyboardType: UIKeyboardType
    public let placeholderLabelModel: UILabelModel
    public let returnKeyType: UIReturnKeyType
    public let textContentType: UITextContentType?
    public let textInset: UIEdgeInsets
    public var textLabelModel: UILabelModel
    public let tintColor: UIColor
    public let trailingTextFieldButtonModel: UIButtonModel?

    // MARK: - Internal Properties

    public init(
        backgroundColor: UIColor,
        cornerRadius: CGFloat = 0,
        enableAutoCorrect: Bool,
        enableWordAutoCapitalization: Bool,
        height: CGFloat,
        isSecureTextEntry: Bool = false,
        keyboardType: UIKeyboardType = .default,
        placeholderLabelModel: UILabelModel,
        returnKeyType: UIReturnKeyType = .done,
        textContentType: UITextContentType? = nil,
        textInset: UIEdgeInsets = .zero,
        textLabelModel: UILabelModel,
        tintColor: UIColor,
        trailingTextFieldButtonModel: UIButtonModel? = nil
    ) {
        self.backgroundColor = backgroundColor
        self.cornerRadius = cornerRadius
        self.enableAutoCorrect = enableAutoCorrect
        self.enableWordAutoCapitalization = enableWordAutoCapitalization
        self.keyboardType = keyboardType
        self.height = height
        self.isSecureTextEntry = isSecureTextEntry
        self.placeholderLabelModel = placeholderLabelModel
        self.returnKeyType = returnKeyType
        self.textContentType = textContentType
        self.textInset = textInset
        self.textLabelModel = textLabelModel
        self.tintColor = tintColor
        self.trailingTextFieldButtonModel = trailingTextFieldButtonModel
    }

}

// MARK: -

open class ModeledTextField: UITextField {

    private var onDeleteBackwards: () -> Void = {}
    private var onEnteredTextChanged: (String) -> Void = { _  in }
    private var inset: UIEdgeInsets = .zero

    // MARK: - Life Cycle

    public override init(frame: CGRect) {
        super.init(frame: frame)

        translatesAutoresizingMaskIntoConstraints = false
        clipsToBounds = true
        setContentCompressionResistancePriority(.defaultLow, for: .horizontal)

        addTarget(self, action: #selector(valueChanged), for: .editingChanged)
    }

    public required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    // MARK: - UITextField

    public override func textRect(forBounds bounds: CGRect) -> CGRect {
        return bounds.inset(by: inset)
    }

    public override func editingRect(forBounds bounds: CGRect) -> CGRect {
        return textRect(forBounds: bounds)
    }

    // MARK: - Public Methods

    public func apply(
        model: UITextFieldModel,
        onEnteredTextChanged: @escaping (String) -> Void,
        onDeleteBackwards: @escaping () -> Void = {}
    ) {
        self.onEnteredTextChanged = onEnteredTextChanged
        self.onDeleteBackwards = onDeleteBackwards

        // Don't use model.placeholderLabelModel.attributedText as-is because we don't want to set the line height
        let attributedPlaceholder = model.placeholderLabelModel.attributedText
        attributedPlaceholder.removeAttribute(.baselineOffset, range: .init(location: 0, length: attributedPlaceholder.length))
        attributedPlaceholder.removeAttribute(.paragraphStyle, range: .init(location: 0, length: attributedPlaceholder.length))
        self.attributedPlaceholder = attributedPlaceholder

        // Only set the text if it has changed (it will only change if the shared code formats it)
        // because we don't want to reset the cursor position
        if attributedText?.string != model.textLabelModel.text {
            // We only want the font and color style from the text label
            // or else the width will be wrong
            attributedText = NSAttributedString(
                string: model.textLabelModel.text,
                attributes: [.font: model.textLabelModel.font, .foregroundColor: model.textLabelModel.textColor]
            )
        }

        backgroundColor = model.backgroundColor
        isSecureTextEntry = model.isSecureTextEntry
        keyboardType = model.keyboardType
        returnKeyType = model.returnKeyType
        textContentType = model.textContentType
        tintColor = model.tintColor
        autocapitalizationType = model.enableWordAutoCapitalization ? .words : .none
        autocorrectionType = model.enableAutoCorrect ? .default : .no

        layer.cornerRadius = model.cornerRadius

        inset = model.textInset

        heightAnchor.constraint(equalToConstant: model.height).isActive = true

        setNeedsLayout()
    }

    @objc
    func valueChanged() {
        onEnteredTextChanged(text ?? "")
    }

    open override func deleteBackward() {
        onDeleteBackwards()
        super.deleteBackward()
    }

}
