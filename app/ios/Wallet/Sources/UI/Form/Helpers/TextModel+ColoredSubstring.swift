import Shared
import SwiftUI

// MARK: -

extension AttributedString {

    static func string(
        from model: LabelModel,
        font: FontTheme
    ) -> AttributedString {
        switch model {
        case let stringModel as LabelModelStringModel:
            return AttributedString(stringModel.string)

        case let stringWithSubstringModel as LabelModelStringWithStyledSubstringModel:
            return stringWithSubstring(stringWithSubstringModel, font: font)
        
        default:
            fatalError("Unexpected LabelModel type")
        }
    }

    static func stringWithSubstring(
        _ model: LabelModelStringWithStyledSubstringModel,
        font: FontTheme
    ) -> AttributedString {
        var attributedString = AttributedString(model.string)
        model.styledSubstrings.forEach { styledSubstring in
            let substringStart = attributedString.index(to: styledSubstring.range.start)
            let substringEnd = attributedString.index(to: styledSubstring.range.endInclusive)
            switch styledSubstring.style {
            case is LabelModelStringWithStyledSubstringModelSubstringStyleBoldStyle:
                attributedString[substringStart...substringEnd].font = font.font.bold()

            case let colorStyle as LabelModelStringWithStyledSubstringModelSubstringStyleColorStyle:
                attributedString[substringStart...substringEnd].foregroundColor = colorStyle.color.nativeColor

            default:
                fatalError("Unexpected substring style")
            }
        }
        return attributedString
    }

}

// MARK: -

extension AttributedString {
    func index(to index: KotlinInt) -> Index {
        return self.index(startIndex, offsetByCharacters: index.intValue)
    }
}

// MARK: -

private extension LabelModelStringWithStyledSubstringModel.Color {
    var nativeColor: Color {
        switch self {
        case .green: return .deviceLEDGreen
        case .blue: return .deviceLEDBlue
        case .on60: return .foreground60
        default: fatalError("Color not implemented")
        }
    }
}
