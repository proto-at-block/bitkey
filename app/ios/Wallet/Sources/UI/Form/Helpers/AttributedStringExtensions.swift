import Shared
import SwiftUI

// Attributed string extensions to help render our shared label model types
extension AttributedString {

    /// Create an attributed string from a given `LabelModel`
    /// - Parameters:
    ///   - model: label model
    ///   - font: font
    ///   - textColor: text color override for sublink text
    /// - Returns: styled attributed string
    static func string(
        from model: LabelModel,
        font: FontTheme,
        textColor: Color? = nil
    ) -> AttributedString {
        switch model {
        case let labelModel as LabelModelStringModel:
            return AttributedString(labelModel.string)
        case let labelModel as LabelModelStringWithStyledSubstringModel:
            return attributedStringWithStyle(labelModel, font: font)
        case let labelModel as LabelModelLinkSubstringModel:
            return attributedStringWithURL(labelModel, font: font, sublinkColor: textColor)
        default:
            fatalError("Unexpected LabelModel type")
        }
    }

    // Apply bold or colored styles to an attributed string from a styled substring model
    private static func attributedStringWithStyle(
        _ model: LabelModelStringWithStyledSubstringModel,
        font: FontTheme
    ) -> AttributedString {
        var attributedString = AttributedString(model.string)
        for styledSubstring in model.styledSubstrings {
            let substringStart = attributedString.index(to: styledSubstring.range.start)
            let substringEnd = attributedString.index(to: styledSubstring.range.endInclusive)
            switch styledSubstring.style {
            case is LabelModelStringWithStyledSubstringModelSubstringStyleBoldStyle:
                attributedString[substringStart ... substringEnd].font = font.font.bold()

            case let colorStyle as LabelModelStringWithStyledSubstringModelSubstringStyleColorStyle:
                attributedString[substringStart ... substringEnd].foregroundColor = colorStyle.color
                    .nativeColor

            default:
                fatalError("Unexpected substring style")
            }
        }
        return attributedString
    }

    // Apply link styling to an attributed string from a link substring model
    private static func attributedStringWithURL(
        _ model: LabelModelLinkSubstringModel,
        font: FontTheme,
        sublinkColor: Color?
    ) -> AttributedString {
        var attributedString = AttributedString(model.string)
        for link in model.linkedSubstrings {
            let substringStart = attributedString.index(to: link.range.start)
            let substringEnd = attributedString.index(to: link.range.endInclusive)
            if model.bold {
                attributedString[substringStart ... substringEnd].font = font.font.bold()
            }
            attributedString[substringStart ... substringEnd].underlineStyle = model
                .underline ? .single : .none
            if model.color != LabelModelColor.unspecified {
                // If the model indicates a specific color for the substring, use that.
                attributedString[substringStart ... substringEnd].foregroundColor = model.color
                    .nativeColor
            } else if let sublinkColor {
                // Otherwise, default to the provided sublink color.
                attributedString[substringStart ... substringEnd].foregroundColor = sublinkColor
            }

            // We use the link range to determine the index in the callback array which is then
            // executed on tap
            attributedString[substringStart ... substringEnd]
                .link = URL(string: linkedSubstringIndex(
                    model,
                    range: link.range
                ) ?? "")
        }
        return attributedString
    }

    private static func linkedSubstringIndex(
        _ model: LabelModelLinkSubstringModel,
        range: KotlinIntRange
    ) -> String? {
        // First, using the range from the linkedSubstring model, we determine the text for a given
        // link
        let str = model.string
        let startIndex = str.index(str.startIndex, offsetBy: Int(range.first))
        let endIndex = str.index(str.startIndex, offsetBy: Int(range.last))
        let linkText = model.string[startIndex ... endIndex]

        // Then, we find that text in our markdown formatted string using regex
        let pattern = "\\[\(linkText)\\]\\(ls:(\\d+)\\)"
        do {
            let regex = try NSRegularExpression(pattern: pattern, options: [])
            // see `fun markdownString()` in LabelModel.kt
            let markdownString = model.markdownString()
            let results = regex.matches(
                in: markdownString,
                options: [],
                range: NSRange(markdownString.startIndex..., in: markdownString)
            )

            for match in results {
                if let callbackRange = Range(match.range(at: 1), in: markdownString) {
                    return String(markdownString[callbackRange])
                }
            }
        } catch {
            log(.error) {
                "failed to construct regular expression for attributed string link with error: \(error.localizedDescription)"
            }
        }

        // if we fail to find a match, return nil
        log(.warn) {
            "attempted to parse a linked substring and failed. model: \(model)"
        }
        return nil
    }
}

extension AttributedString {
    func index(to index: KotlinInt) -> Index {
        return self.index(startIndex, offsetByCharacters: index.intValue)
    }
}

private extension LabelModelColor {
    var nativeColor: Color {
        switch self {
        case .green: return .deviceLEDGreen
        case .blue: return .deviceLEDBlue
        case .on60: return .foreground60
        case .primary: return .bitkeyPrimary
        default: fatalError("Color not implemented")
        }
    }
}
