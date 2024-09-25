import Shared
import SwiftUI

// MARK: -

public struct TextModel {

    // MARK: - Public Types

    public enum Content {
        case text(String)
        case attributedText(AttributedString)
        case linkedText(
            string: AttributedString,
            links: [LabelModelLinkSubstringModel.LinkSubstring]
        )
    }

    public enum Width {
        case hug
        case fill(textAlignment: TextAlignment)
    }

    // MARK: - Public Properties

    public let content: Content
    public let font: Font
    public let kerning: CGFloat
    public let baselineOffset: CGFloat
    public let lineSpacing: CGFloat
    public let textColor: Color
    public let width: Width
    public let treatment: LabelTreatment
    public let scalingFactor: CGFloat?

    // MARK: - Life Cycle

    public init(
        content: Content,
        font: Font,
        kerning: CGFloat,
        baselineOffset: CGFloat,
        lineSpacing: CGFloat,
        textAlignment: TextAlignment?,
        width: Width? = nil,
        textColor: Color,
        treatment: LabelTreatment,
        scalingFactor: CGFloat? = nil
    ) {
        self.content = content
        self.font = font
        self.kerning = kerning
        self.baselineOffset = baselineOffset
        self.lineSpacing = lineSpacing
        self.textColor = textColor

        if let width {
            self.width = width
        } else if let textAlignment {
            self.width = .fill(textAlignment: textAlignment)
        } else {
            self.width = .hug
        }
        self.treatment = treatment
        self.scalingFactor = scalingFactor
    }

}

// MARK: -

public struct ModeledText: View {

    private let model: TextModel

    public init(model: TextModel) {
        self.model = model
    }

    public var body: some View {
        Group {
            switch model.content {
            case let .text(string):
                Text(string)
                    .if(model.treatment == .strikethrough) { text in
                        text.strikethrough()
                    }
                    .ifNonnull(model.scalingFactor) { text, factor in
                        text.lineLimit(1)
                            .minimumScaleFactor(factor)
                    }
            case let .attributedText(attributedString):
                Text(attributedString)
            case let .linkedText(attributedString, links):
                Text(attributedString).environment(\.openURL, OpenURLAction { url in
                    // We pass the callback index as the URL string value, see
                    // AttributedStringExtensions.swift
                    if let callbackIndex = Int(url.absoluteString),
                       links.count - 1 >= callbackIndex
                    {
                        links[callbackIndex].onClick()
                        return .handled
                    } else {
                        return .discarded
                    }
                })
            }
        }
        .font(model.font)
        .foregroundColor(model.textColor)
        .kerningIfAvailable(model.kerning)
        .lineSpacing(model.lineSpacing)
        .width(model.width)
        .fixedSize(horizontal: false, vertical: true)
    }

}

// MARK: -

private extension View {
    @ViewBuilder
    func kerningIfAvailable(_ kern: CGFloat) -> some View {
        // kerning only available on iOS 16+
        if #available(iOS 16.0, *) {
            kerning(kern)
        } else {
            self
        }
    }

    @ViewBuilder
    func width(_ width: TextModel.Width) -> some View {
        switch width {
        case let .fill(textAlignment):
            self.multilineTextAlignment(textAlignment)
                .frame(maxWidth: .infinity, alignment: textAlignment.alignment)
        case .hug:
            self
        }
    }
}

// MARK: -

extension TextAlignment {
    var alignment: Alignment {
        switch self {
        case .leading: return .leading
        case .center: return .center
        case .trailing: return .trailing
        }
    }
}

// MARK: -

public enum LabelTreatment {
    case unspecified
    case strikethrough
}
