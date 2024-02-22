import Shared
import SwiftUI
import UIKit

// MARK: -

extension TextFieldModel.KeyboardType {

    /**
     * The iOS `UIKeyboardType` that corresponds to the Shared keyboard type
     */
    var nativeModel: UIKeyboardType {
        switch self {
        case .email:
            return .emailAddress
        case .uri:
            return .URL
        case .decimal:
            return .decimalPad
        case .number:
            return .numberPad
        case .phone:
            return .phonePad
        case .default_:
            return .default
        default:
            fatalError("Unhandled text input keyboard type")
        }
    }

    /**
     * Guess the iOS-specific `UITextContentType` based on the keyboard type
     */
    var textContentType: UITextContentType? {
        switch self {
        case .email:
            return .emailAddress
        case .uri:
            return .URL
        case .decimal:
            return nil
        case .number:
            return nil
        case .phone:
            return .telephoneNumber
        case .default_:
            return nil
        default:
            fatalError("Unhandled text input keyboard type")
        }
    }

}
