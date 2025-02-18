import SwiftUI
import UIKit

// MARK: -

extension UIColor {

    convenience init(
        light lightModeColor: UIColor,
        dark darkModeColor: UIColor
    ) {
        self.init(
            dynamicProvider: { traitCollection in
                switch traitCollection.userInterfaceStyle {
                case .light, .unspecified:
                    return lightModeColor
                case .dark:
                    return darkModeColor
                @unknown default:
                    return lightModeColor
                }
            }
        )
    }

}

// MARK: -

extension Color {

    init(
        from uiColor: UIColor
    ) {
        if #available(iOS 15.0, *) {
            self.init(uiColor: uiColor)
        } else {
            self.init(uiColor)
        }
    }

}
