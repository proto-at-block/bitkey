import UIKit

public protocol Application: NSObject {
    var applicationIconBadgeNumber: Int { get set }
}

extension UIApplication: Application {}
