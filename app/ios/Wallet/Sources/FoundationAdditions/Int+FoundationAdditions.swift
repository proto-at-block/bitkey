import CoreGraphics
import Foundation
import Shared

extension Int {

    /// Convenience getter for the CGFloat value of an integer.
    public var CGFloatValue: CGFloat {
        return CGFloat(self)
    }

    /// Convenience for literal-looking CGFloat declarations,
    /// ie. `let margin = 10.f` which is much cleaner than `let margin = CGFloat(10)`.
    public var f: CGFloat {
        return self.CGFloatValue
    }

}

extension Int32 {

    /// Convenience getter for the CGFloat value of an integer.
    public var CGFloatValue: CGFloat {
        return CGFloat(self)
    }

    /// Convenience for literal-looking CGFloat declarations,
    /// ie. `let margin = 10.f` which is much cleaner than `let margin = CGFloat(10)`.
    public var f: CGFloat {
        return self.CGFloatValue
    }

}
