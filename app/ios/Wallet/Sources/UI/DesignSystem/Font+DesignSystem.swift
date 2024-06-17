import SwiftUI
import UIKit

// MARK: -

public struct UIFontTheme {

    public let font: UIFont

    public let lineHeight: CGFloat

    public let kerning: CGFloat

    public init(name: String, size: String, lineHeight: String, kerning: String) {
        self.font = UIFont.make(name: name, size: size)
        self.lineHeight = CGFloat(Int(lineHeight)!)
        self.kerning = CGFloat(Double(kerning)!)
    }

}

// MARK: -

public struct FontTheme {

    public let font: Font

    public let baselineOffset: CGFloat

    public let lineSpacing: CGFloat

    public let kerning: CGFloat

    public init(name: String, size: String, lineHeight: String, kerning: String) {
        self.font = Font.make(name: name, size: size)
        let lineHeight = CGFloat(Int(lineHeight)!)
        let fontLineHeight = UIFont.make(name: name, size: size).lineHeight
        self.baselineOffset = (lineHeight - fontLineHeight) / 4
        self.lineSpacing = lineHeight - fontLineHeight
        self.kerning = CGFloat(Double(kerning)!)
    }

}

// MARK: -

extension Font {

    /// Convenience initializer to build a `Font` with a string size.
    static func make(name: String, size: String) -> Font {
        _ = UIFont.registerWeightsOnce
        return .custom(name, size: CGFloat(Int(size)!))
    }

}

// MARK: -

extension UIFont {

    // MARK: - Convenience Initializer

    /// Convenience initializer to build a `UIFont` with a string size.
    static func make(name: String, size: String) -> UIFont {
        _ = UIFont.registerWeightsOnce
        return .init(name: name, size: CGFloat(Int(size)!))!
    }

    // MARK: - Internal Static Methods

    static let registerWeightsOnce: () = {
        // We call `familyNames` before registering fonts to avoid a possible deadlock bug.
        _ = UIFont.familyNames

        for name in FontName.allCases {
            guard let fontURL = Bundle.main.url(
                forResource: name.rawValue, withExtension: "otf"
            ) ?? Bundle.main.url(
                forResource: name.rawValue, withExtension: "ttf"
            ) else {
                assertionFailure("Unable to find font resource: \(name)")
                continue
            }
            var errorReference: Unmanaged<CFError>?
            if CTFontManagerRegisterFontsForURL(fontURL as CFURL, .process, &errorReference) {
                continue
            }
            let error = errorReference!.takeRetainedValue()
            assertionFailure("Unable to register font: \(name): \(error.localizedDescription)")
        }
    }()

}
