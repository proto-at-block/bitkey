import UIKit

// Selecting the use of UITextView and UITextField is typically a pain, because the former
// requires you to give up being able to implement placeholder text, while the latter does
// not allow for multiline support at all.
//
// TextField(text:axis:) is an option in SwiftUI, but unfortunately is only available on
// iOS 16 upwards.
//
// ExpandableTextField is basically a UITextView implemented with placeholder functionality.
// We achieve this by subclassing UITextView, and adding UITextField as a way to mimic
// the nice placeholder behavior we get for free there. It also allows us to take advantage
// of the accessory views we can attach to UITextField.
//

// MARK: - View Code

public class ExpandableTextField: UITextView, UITextViewDelegate {
    private var placeholderTextField = PlaceholderTextField()
    private var accessoryButtonView: ModeledButton = .init()

    private var onEnteredTextChanged: (String, NSRange) -> Void = { _, _ in }
    private var inset: UIEdgeInsets = .zero

    private var placeholderTextFieldHeightConstraint: NSLayoutConstraint?

    // MARK: - Overriden Internal Types

    // The properties here require us to update the "dummy" placeholder label in order to keep them
    // in sync
    // and looking the same.
    override open var textContainerInset: UIEdgeInsets {
        didSet {
            updatePlaceholderFieldInsets()
        }
    }

    override public var contentSize: CGSize {
        didSet {
            var topCorrection = (bounds.size.height - contentSize.height * zoomScale) / 2.0
            topCorrection = max(0, topCorrection)
            contentInset = UIEdgeInsets(top: topCorrection, left: 0, bottom: 0, right: 0)
        }
    }

    // MARK: - Life Cycle

    override public init(frame: CGRect, textContainer: NSTextContainer?) {
        super.init(frame: frame, textContainer: textContainer)

        self.delegate = self
        setup()
    }

    convenience init() {
        self.init(frame: CGRectZero, textContainer: nil)
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("This class doesn't support NSCoding.")
    }

    override public func layoutSubviews() {
        super.layoutSubviews()
        // Do not scroll the background text field
        placeholderTextField.frame = CGRectMake(
            0,
            self.contentOffset.y,
            self.frame.width,
            self.frame.height
        )
    }

    // MARK: - Public Methods

    public func apply(
        model: UITextFieldModel,
        onEnteredTextChanged: @escaping (String, NSRange) -> Void
    ) {
        self.onEnteredTextChanged = onEnteredTextChanged

        placeholderTextField.attributedPlaceholder = model.placeholderLabelModel.attributedText
        attributedText = model.textLabelModel.attributedText

        // Here is where we take advantage of the placeholder display behavior of `UITextField`.
        // Specifically,
        // the placeholder appears if the text field is empty, and disappears if the text field has
        // some
        // value.
        //
        // Since we set the textColor of `placeholderTextField` to `clear`, the user never actually
        // sees the text
        // that gets set.
        placeholderTextField.text = model.textLabelModel.text

        // Since a prior call to `apply` may have created the button, we still need to manually
        // hide/show the accessory button.
        if let accessoryButtonModel = model.trailingTextFieldButtonModel {
            accessoryButtonView.apply(model: accessoryButtonModel)
            accessoryButtonView.isHidden = false
        } else {
            accessoryButtonView.isHidden = true
        }

        // For some reason, font and size updates are always flakey for UITextView.
        font = model.textLabelModel.font

        keyboardType = model.keyboardType
        returnKeyType = model.returnKeyType
        tintColor = model.tintColor

        placeholderTextField.backgroundColor = model.backgroundColor
        placeholderTextField.layer.cornerRadius = model.cornerRadius

        textContainerInset = model.textInset
        placeholderTextFieldHeightConstraint?.constant = model.height

        setNeedsLayout()
    }

    public func textViewDidChange(_: UITextView) {
        onEnteredTextChanged(text ?? "", selectedRange)
    }
}

extension ExpandableTextField {
    // We need this subclass so that we can pass it our inset settings.
    class PlaceholderTextField: UITextField {
        var inset: UIEdgeInsets = .zero

        override func textRect(forBounds bounds: CGRect) -> CGRect {
            return bounds.inset(by: inset)
        }

        override func placeholderRect(forBounds bounds: CGRect) -> CGRect {
            return textRect(forBounds: bounds)
        }
    }
}

// MARK: - View setup code.

// Setup code to get intended view behavior of an expandable text field, backed by an unscrollable
// UITextView with a UITextField subview in order to be able to show a placeholder.
extension ExpandableTextField {
    private func setup() {
        translatesAutoresizingMaskIntoConstraints = false
        placeholderTextField.translatesAutoresizingMaskIntoConstraints = false
        accessoryButtonView.translatesAutoresizingMaskIntoConstraints = false

        isScrollEnabled = false

        // Use a UITextField to pose as a "background" that gets shown/hidden depending on whether
        // or not
        // the user has entered some text
        placeholderTextField.borderStyle = .roundedRect
        placeholderTextField.textColor = .clear
        placeholderTextField.isUserInteractionEnabled = false
        placeholderTextField.borderStyle = .none

        self.addSubview(placeholderTextField)
        self.sendSubviewToBack(placeholderTextField)
        self.addSubview(accessoryButtonView)

        setupAccessoryButtonViewConstraints()
        setupConstraints()
        updatePlaceholderFieldInsets()
    }

    private func setupConstraints() {
        placeholderTextFieldHeightConstraint = placeholderTextField.heightAnchor
            .constraint(equalToConstant: 0)
        // Bump priority down to avoid issues with _UITemporaryLayoutHeight
        placeholderTextFieldHeightConstraint?.priority = UILayoutPriority(999)
        placeholderTextFieldHeightConstraint?.isActive = true
    }

    private func setupAccessoryButtonViewConstraints() {
        // For some reason, the trailing anchor of the UITextView ends up being at the leading
        // margin.
        // Therefore, we offset the constraint by the entire width of the text view, with a margin
        // of 12.
        NSLayoutConstraint.activate([
            accessoryButtonView.trailingAnchor.constraint(
                equalTo: frameLayoutGuide.trailingAnchor,
                constant: -12
            ),
            accessoryButtonView.centerYAnchor.constraint(equalTo: centerYAnchor),
        ])
    }

    // Updates the placeholderLabel constraints constants to match the textContainerInset and
    // textContainer
    private func updatePlaceholderFieldInsets() {
        placeholderTextField.inset = UIEdgeInsets(
            top: self.textContainerInset.top,
            left: self.textContainerInset.left + self.textContainer.lineFragmentPadding,
            bottom: self.textContainerInset.bottom,
            right: self.textContainerInset.right
        )
    }
}
