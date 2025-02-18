import SwiftUI
import UIKit

public extension UIColor {
    // nfc blue
    static let nfcBlue = UIColor(
        light: UIColor(red: 0.239, green: 0.506, blue: 1.000, alpha: 1),
        dark: UIColor(red: 0.239, green: 0.506, blue: 1.000, alpha: 1)
    )
}

public extension Color {
    static let nfcBlue = Color(from: .nfcBlue)
}
