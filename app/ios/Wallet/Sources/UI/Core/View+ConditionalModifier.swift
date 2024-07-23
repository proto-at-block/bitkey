import SwiftUI

public extension View {

    /// Applies the given transform if the given condition evaluates to `true`.
    /// - Parameters:
    ///   - condition: The condition to evaluate.
    ///   - transform: The transform to apply to the source `View`.
    /// - Returns: Either the original `View` or the modified `View` if the condition is `true`.
    @ViewBuilder func `if`(
        _ condition: Bool,
        transform: (Self) -> some View
    ) -> some View {
        if condition {
            transform(self)
        } else {
            self
        }
    }

    @ViewBuilder
    func ifNonnull<T: Any>(
        _ nullableItem: T?,
        transform: (Self, T) -> some View
    ) -> some View {
        if let item = nullableItem {
            transform(self, item)
        } else {
            self
        }
    }

}
