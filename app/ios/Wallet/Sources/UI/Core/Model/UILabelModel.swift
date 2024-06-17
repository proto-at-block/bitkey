import UIKit

// MARK: -

public struct UILabelModel {

    // MARK: - Public Properties

    public let numberOfLines: Int
    public let textAlignment: NSTextAlignment
    public let textColor: UIColor
    public let font: UIFont
    public let kerning: CGFloat

    public var text: String {
        get { return attributedText.string }
        set { attributedText.replaceCharacters(
            in: NSRange(0 ..< attributedText.string.count),
            with: newValue
        ) }
    }

    // MARK: - Internal Properties

    private(set) var attributedText: NSMutableAttributedString

    public init(
        font: UIFont,
        kerning: CGFloat,
        lineHeight: CGFloat,
        numberOfLines: Int = 0,
        text: String,
        textAlignment: NSTextAlignment = .left,
        textColor: UIColor,
        attributedSubstrings: [AttributedSubstringModel] = []
    ) {
        self.numberOfLines = numberOfLines
        self.textAlignment = textAlignment
        self.textColor = textColor
        self.font = font
        self.kerning = kerning
        self.attributedText = NSMutableAttributedString(string: text)

        let paragraphStyle = NSMutableParagraphStyle()
        paragraphStyle.minimumLineHeight = lineHeight

        attributedText.addAttributes(
            [
                .font: font,
                .kern: kerning,
                .foregroundColor: textColor,
                .paragraphStyle: paragraphStyle,
                // Add an offset so that the text is centered vertically based on the line height
                .baselineOffset: (lineHeight - font.lineHeight) / 4,
            ],
            range: NSMakeRange(0, text.count)
        )

        for attributedSubstring in attributedSubstrings {
            attributedText.addAttribute(
                attributedSubstring.key,
                value: attributedSubstring.value,
                range: attributedSubstring.range
            )
        }
    }

}

// MARK: -

public extension UILabel {

    func apply(model: UILabelModel) {
        attributedText = model.attributedText
        numberOfLines = model.numberOfLines
        textAlignment = model.textAlignment
        lineBreakMode = .byWordWrapping
    }

}
