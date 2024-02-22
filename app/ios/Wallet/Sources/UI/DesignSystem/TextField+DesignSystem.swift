import UIKit

// MARK: -

public extension UITextFieldModel {

    static func standard(
        placeholder: String,
        text: String = "",
        isSecureTextEntry: Bool = false,
        keyboardType: UIKeyboardType = .default,
        textContentType: UITextContentType? = nil,
        textInset: UIEdgeInsets = .init(top: 0, left: 24, bottom: 0, right: 24),
        enableAutoCorrect: Bool = false,
        enableWordAutoCapitalization: Bool = false
    ) -> UITextFieldModel {
        return .init(
            backgroundColor: .foreground10,
            cornerRadius: DesignSystemMetrics.textFieldHeight / 2,
            enableAutoCorrect: enableAutoCorrect,
            enableWordAutoCapitalization: enableWordAutoCapitalization,
            height: DesignSystemMetrics.textFieldHeight,
            isSecureTextEntry: isSecureTextEntry,
            keyboardType: keyboardType,
            placeholderLabelModel: .standard(placeholder, font: .body2Regular, textColor: .foreground60),
            textInset: textInset,
            textLabelModel: .standard(text, font: .body2Regular),
            tintColor: .foreground
        )
    }
    
    // An expandable variant designed to be used with ExpandableTextField.
    //
    // It is different from the .standard variant above, because ExpandableTextField's underlying
    // UITextView does not have a vertical text inset setting by default.
    static func standardExpandable(
        placeholder: String,
        keyboardType: UIKeyboardType = .default,
        font: UIFontTheme = .body2Regular,
        textLabelModel: UILabelModel = .standard("", font: .body2Regular),
        trailingButtonModel: UIButtonModel? = nil
    ) -> UITextFieldModel {
        return .init(
            backgroundColor: .foreground10,
            cornerRadius: DesignSystemMetrics.textFieldHeight / 2,
            enableAutoCorrect: false,
            enableWordAutoCapitalization: false,
            height: DesignSystemMetrics.textFieldHeight,
            keyboardType: keyboardType,
            placeholderLabelModel: .standard(placeholder, font: .body2Regular, textColor: .foreground60),
            textInset: .init(top: 16, left: 12, bottom: 16, right: 12),
            textLabelModel: textLabelModel,
            tintColor: .foreground,
            trailingTextFieldButtonModel: trailingButtonModel
        )
    }
}
