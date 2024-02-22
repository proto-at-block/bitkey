import UIKit

// MARK: -

public class QRCodeScannerViewControllerFactoryImpl: QRCodeScannerViewControllerFactory {

    public init() {}

    public func make(
        onClose: @escaping () -> Void,
        onQrCodeScanned: @escaping (String) -> Void
    ) -> QRCodeScannerViewController {
        let presenter = QRCodeScannerPresenterImpl(
            onClose: onClose,
            onQrCodeScanned: onQrCodeScanned
        )
        return QRCodeScannerViewControllerImpl(presenter: presenter)
    }

}
