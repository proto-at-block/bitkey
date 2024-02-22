import Foundation
import UIKit

// MARK: -

public protocol QRCodeScannerViewController: UIViewController {

    func apply(model: QRCodeScannerViewControllerModel)

    func setBackgroundColor(_ color: UIColor)

}
