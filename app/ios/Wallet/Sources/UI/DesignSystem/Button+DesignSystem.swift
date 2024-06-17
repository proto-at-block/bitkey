import Shared
import SwiftUI
import UIKit

// MARK: -

public extension ButtonModel {

    static func tertiaryDestructive(
        text: String,
        onClick: @escaping () -> Void
    ) -> ButtonModel {
        return ButtonModel(
            text: text,
            isEnabled: true,
            isLoading: false,
            leadingIcon: nil,
            treatment: .tertiarydestructive,
            size: .footer,
            testTag: nil,
            onClick: StandardClick(onClick: onClick)
        )
    }

}

public extension ButtonModel {
    var backgroundColor: Color {
        return isEnabled
            ? normalBackgroundColor
            : disabledBackgroundColor
    }

    var titleColor: Color {
        switch treatment {
        case .black: return .primaryForeground
        case .primary: return .primaryForeground
        case .primarydanger: return .danger
        case .primarydestructive: return .primaryForeground
        case .secondary: return .secondaryForeground
        case .secondarydestructive: return .destructive
        case .tertiary: return .foreground
        case .tertiarynounderline: return .foreground
        case .tertiaryprimary: return .primary
        case .tertiaryprimarynounderline: return .primary
        case .tertiarydestructive: return .destructiveForeground
        case .translucent: return .translucentForeground
        case .translucent10: return .translucentForeground
        case .white: return .black
        case .warning: return .warning
        default:
            fatalError("Unhandled ButtonModel for treatement \(treatment)")
        }
    }

    var loadingTint: RotatingLoadingIcon.Tint {
        switch treatment {
        case .black: return .white
        case .primary: return .white
        case .primarydanger: return .white
        case .primarydestructive: return .white
        case .secondary: return .black
        case .secondarydestructive: return .black
        case .tertiary: return .black
        case .tertiarynounderline: return .black
        case .tertiaryprimary: return .black
        case .tertiaryprimarynounderline: return .black
        case .tertiarydestructive: return .black
        case .translucent: return .white
        case .translucent10: return .white
        case .white: return .black
        default:
            fatalError("Unhandled ButtonModel for treatement \(treatment)")
        }
    }

    private var normalBackgroundColor: Color {
        switch treatment {
        case .black: return .black
        case .primary: return .primary
        case .primarydanger: return .dangerBackground
        case .primarydestructive: return .destructive
        case .secondary: return .secondary
        case .secondarydestructive: return .secondary
        case .tertiary: return .clear
        case .tertiarynounderline: return .clear
        case .tertiaryprimary: return .clear
        case .tertiaryprimarynounderline: return .clear
        case .tertiarydestructive: return .clear
        case .translucent: return .translucentButton20
        case .translucent10: return .translucentButton10
        case .white: return .white
        case .warning: return .warningForeground
        default:
            fatalError("Unhandled ButtonModel for treatement \(treatment)")
        }
    }

    private var disabledBackgroundColor: Color {
        switch treatment {
        case .black: return .black.opacity(0.4)
        case .primary: return .primary.opacity(0.4)
        case .primarydanger: return .secondary
        case .primarydestructive: return .destructive.opacity(0.4)
        case .secondary: return .secondary
        case .secondarydestructive: return .secondary
        case .tertiary: return .clear
        case .tertiarynounderline: return .clear
        case .tertiaryprimary: return .clear
        case .tertiaryprimarynounderline: return .clear
        case .tertiarydestructive: return .clear
        case .translucent: return .translucentButton20
        case .translucent10: return .translucentButton10
        case .white: return .white.opacity(0.4)
        case .warning: return .warningForeground.opacity(0.4)
        default:
            fatalError("Unhandled ButtonModel for treatement \(treatment)")
        }
    }
}

// MARK: -

public extension UIButtonModel {

    /// Returns a title-based button with customizable styling
    static func makeButton(
        backgroundColor: UIColor,
        title: String,
        titleColor: UIColor,
        titleFont: UIFontTheme = .label1,
        height: CGFloat = DesignSystemMetrics.Button.height,
        icon: UIImage? = nil,
        isEnabled: Bool = true,
        isLoading: Bool = false,
        width: CGFloat? = nil,
        isTextOnly: Bool = false,
        action: @escaping () -> Void
    ) -> UIButtonModel {
        return .init(
            backgroundColor: backgroundColor,
            highlightedBackgroundColor: backgroundColor.highlightedColor,
            height: height,
            image: icon?.withTintColor(titleColor),
            isEnabled: isEnabled,
            isLoading: isLoading,
            title: title,
            titleColor: titleColor,
            titleFont: titleFont.font,
            titleKerning: titleFont.kerning,
            width: width,
            isTextOnly: isTextOnly,
            action: action
        )
    }

}
