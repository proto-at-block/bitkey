import Foundation

public struct AttributedSubstringModel {

    public let key: NSAttributedString.Key
    public let value: Any
    public let range: NSRange

    public init(key: NSAttributedString.Key, value: Any, range: NSRange) {
        self.key = key
        self.value = value
        self.range = range
    }

    /// Convenience initializer where the range will be calculated as the first occurrence of the substring in the given text
    public init?(key: NSAttributedString.Key, value: Any, substring: String, in text: String) {
        guard let range = NSRange(substring, in: text) else { return nil }

        self.key = key
        self.value = value
        self.range = range
    }

}
