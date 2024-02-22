
import Foundation

public extension NSRange {

    /// Creates an `NSRange` for a given substring within a given string.
    init?(_ substring: String, in string: String) {
        guard let subrange = string.range(of: substring) else {
            return nil
        }
        self.init(
            subrange,
            in: string
        )
    }

}
