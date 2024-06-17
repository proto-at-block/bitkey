import CoreGraphics
import Foundation
import Shared

public extension Int {

    /// Convenience getter for the CGFloat value of an integer.
    var CGFloatValue: CGFloat {
        return CGFloat(self)
    }

    /// Convenience for literal-looking CGFloat declarations,
    /// ie. `let margin = 10.f` which is much cleaner than `let margin = CGFloat(10)`.
    var f: CGFloat {
        return self.CGFloatValue
    }

}

public extension Int32 {

    /// Convenience getter for the CGFloat value of an integer.
    var CGFloatValue: CGFloat {
        return CGFloat(self)
    }

    /// Convenience for literal-looking CGFloat declarations,
    /// ie. `let margin = 10.f` which is much cleaner than `let margin = CGFloat(10)`.
    var f: CGFloat {
        return self.CGFloatValue
    }

}
