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
        width: Width? = nil,
        textColor: Color = .foreground,
        treatment: LabelTreatment = .unspecified,
        scalingFactor: CGFloat? = nil
    ) -> TextModel {
        return .init(
            content: .text(text),
            font: font.font,
            kerning: font.kerning,
            baselineOffset: font.baselineOffset,
            lineSpacing: font.lineSpacing,
            textAlignment: textAlignment,
            width: width,
            textColor: textColor,
            treatment: treatment,
            scalingFactor: scalingFactor
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

    private static func linkedText(
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

    // Derives the appropriate TextModel for the provided LabelModel, handling
    // text styling and sublinks.
    static func fromModel(
        model: LabelModel,
        font: FontTheme,
        textColor: Color = .foreground
    ) -> TextModel {
        return switch model {
        case let model as LabelModelStringModel:
            .standard(model.string, font: font, textColor: textColor)

        case let model as LabelModelStringWithStyledSubstringModel:
            .standard(.string(from: model, font: font), font: font, textColor: textColor)

        case let model as LabelModelLinkSubstringModel:
            .linkedText(
                textContent: .linkedText(
                    string: .string(from: model, font: font, textColor: textColor),
                    links: model.linkedSubstrings
                ),
                font: font,
                textColor: textColor
            )

        default:
            fatalError("Unexpected Kotlin LabelModel")
        }
    }
}
