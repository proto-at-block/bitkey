import SwiftUI

public extension View {
    /// Extension to animate numeric text. Used for amount strings that change (i.e. via slider or
    /// key entry)
    func numericTextAnimation(numericText: some Equatable) -> some View {
        modifier(NumericTextAnimation(numericText: numericText))
    }
}

// MARK: -

private struct NumericTextAnimation<T: Equatable>: ViewModifier {
    var numericText: T
    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content
                .contentTransition(.numericText())
                .animation(.interpolatingSpring, value: numericText)
        } else {
            content
                .animation(.interpolatingSpring, value: numericText)
        }
    }
}

private extension Animation {
    static var interpolatingSpring: Animation {
        return .interpolatingSpring(mass: 1.0, stiffness: 0.0, damping: 0.0, initialVelocity: 0.0)
    }
}
