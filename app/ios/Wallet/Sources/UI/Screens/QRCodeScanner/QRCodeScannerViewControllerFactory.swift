import Foundation
import UIKit

// MARK: -

public protocol QRCodeScannerViewControllerFactory {

    func make(
        onClose: @escaping () -> Void,
        onQrCodeScanned: @escaping (String) -> Void
    ) -> QRCodeScannerViewController

}
