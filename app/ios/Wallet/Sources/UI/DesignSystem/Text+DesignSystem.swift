import Shared
import SwiftUI
import UIKit

// MARK: -

public extension UILabelModel {

    static func standard(
        _ text: String,
        font: UIFontTheme,
        textAlignment: NSTextAlignment = .left,
        textColor: UIColor = .foreground,
        attributedSubstrings: [AttributedSubstringModel] = []
    ) -> UILabelModel {
        return .init(
            font: font.font,
            kerning: font.kerning,
            lineHeight: font.lineHeight,
            text: text,
            textAlignment: textAlignment,
            textColor: textColor,
            attributedSubstrings: attributedSubstrings
        )
    }

}

// MARK: -

public extension TextModel {

    static func standard(
        _ text: String,
        font: FontTheme,
        textAlignment: TextAlignment? = .leading,
        textColor: Color = .foreground,
        treatment: LabelTreatment = .unspecified
    ) -> TextModel {
        return .init(
            content: .text(text),
            font: font.font,
            kerning: font.kerning,
            baselineOffset: font.baselineOffset,
            lineSpacing: font.lineSpacing,
            textAlignment: textAlignment,
            textColor: textColor,
            treatment: treatment
        )
    }

    static func standard(
        _ text: AttributedString,
        font: FontTheme,
        textAlignment: TextAlignment = .leading,
        textColor: Color = .foreground
    ) -> TextModel {
        return .init(
            content: .attributedText(text),
            font: font.font,
            kerning: font.kerning,
            baselineOffset: font.baselineOffset,
            lineSpacing: font.lineSpacing,
            textAlignment: textAlignment,
            textColor: textColor,
            treatment: .unspecified
        )
    }

    static func linkedText(
        textContent: TextModel.Content,
        font: FontTheme,
        textAlignment: TextAlignment = .leading,
        textColor: Color = .foreground
    ) -> TextModel {
        return .init(
            content: textContent,
            font: font.font,
            kerning: font.kerning,
            baselineOffset: font.baselineOffset,
            lineSpacing: font.lineSpacing,
            textAlignment: textAlignment,
            textColor: textColor,
            treatment: .unspecified
        )
    }
}
