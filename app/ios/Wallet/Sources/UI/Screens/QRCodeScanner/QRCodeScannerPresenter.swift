import AVFoundation
import Foundation

// MARK: -

public protocol QRCodeScannerPresenter {

    var captureSession: AVCaptureSession { get }

    var viewController: QRCodeScannerViewController! { get set }

    func viewWillAppear()

    func viewDidAppear()

    func viewWillDisappear()

}
