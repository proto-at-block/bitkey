import AVFoundation
import Foundation
import Shared
import UIKit

// MARK: -

public final class QRCodeScannerPresenterImpl: NSObject, QRCodeScannerPresenter {

    // MARK: - Internal Types

    enum InternalAction {
        case didCancelCameraDeniedAlert
        case didConfirmCameraDeniedAlert
        case didCancelCameraUnavailableAlert
    }

    // MARK: - Public Properties

    public let captureSession: AVCaptureSession

    public weak var viewController: QRCodeScannerViewController!

    // MARK: - Private Properties

    /// The queue used for configuring, starting, and stopping the capture session.
    private let captureSessionQueue: DispatchQueue

    private let onClose: () -> Void
    private let onQrCodeScanned: (String) -> Void

    /// When a QR code is scanned, we store it /here.
    /// This is used to de-dupe (and ignore) subsequent scans of the same code.
    private var lastHandledQRCodeString: String?

    // MARK: - Life Cycle

    public required init(
        onClose: @escaping () -> Void,
        onQrCodeScanned: @escaping (String) -> Void
    ) {
        self.captureSession = AVCaptureSession()
        self.captureSessionQueue = DispatchQueue(label: "captureSessionQueue", qos: .userInitiated)
        self.onClose = onClose
        self.onQrCodeScanned = onQrCodeScanned
    }

    // MARK: - QRCodeScannerPresenter

    public func viewWillAppear() {
        handleCurrentAuthorizationStatus()
    }

    public func viewDidAppear() {
        // Resetting `lastHandledQRCodeString` to nil allows the QR scanner to function again if we navigate
        // back to this view by popping a page pushed onto the navigation stack.
        lastHandledQRCodeString = nil
    }

    public func viewWillDisappear() {
        removeCaptureSessionInputsAndOutputsAndStopRunning()
    }

    // MARK: - Private Methods - Actions

    private func handle(_ action: InternalAction) {
        switch action {
        case .didCancelCameraUnavailableAlert, .didCancelCameraDeniedAlert:
            onClose()
        case .didConfirmCameraDeniedAlert:
            // Open system settings
            let urlOpener = UIApplication.shared
            if let settingsURL = URL(string: UIApplication.openSettingsURLString),
               urlOpener.canOpenURL(settingsURL) {
                urlOpener.open(settingsURL) { [weak self] _ in
                    self?.onClose()
                }
            } else {
                // If for some reason we can't open settings, log an error and dismiss.
                log(.error) { "Invalid System settings URL url=\(UIApplication.openSettingsURLString)" }
                onClose()
            }
        }
    }

    // MARK: - Private Methods - Capture Session

    private func configureCaptureSessionAndStartRunning() {
        captureSessionQueue.async { [weak captureSession] in
            guard let captureSession = captureSession else { return }

            let metadataOutput = AVCaptureMetadataOutput()
            metadataOutput.setMetadataObjectsDelegate(self, queue: .main)

            // Check that the inputs and outputs are valid, otherwise show an alert.
            guard let captureDevice = AVCaptureDevice.default(for: .video),
                  let input = try? AVCaptureDeviceInput(device: captureDevice),
                  captureSession.canAddInput(input),
                  captureSession.canAddOutput(metadataOutput)
            else {
                DispatchQueue.main.async { [weak self] in
                    self?.presentCameraUnavailableAlert()
                }
                return
            }

            captureSession.beginConfiguration()

            captureSession.addInput(input)
            captureSession.addOutput(metadataOutput)

            // Check that the session supports QR codes.
            guard metadataOutput.availableMetadataObjectTypes.contains(.qr) else {
                DispatchQueue.main.async { [weak self] in
                    self?.presentCameraUnavailableAlert()
                }
                return
            }

            metadataOutput.metadataObjectTypes = [.qr]

            captureSession.commitConfiguration()
            captureSession.startRunning()

            // Update the view to a clear background now that we know we can use the camera
            DispatchQueue.main.async { [weak self] in
                self?.viewController.setBackgroundColor(.clear)
            }
        }
    }

    private func removeCaptureSessionInputsAndOutputsAndStopRunning() {
        captureSessionQueue.async { [weak captureSession] in
            guard let captureSession = captureSession else { return }
            captureSession.beginConfiguration()
            captureSession.removeAllInputs()
            captureSession.removeAllOutputs()
            captureSession.commitConfiguration()
            captureSession.stopRunning()
        }
    }

    // MARK: - Private Methods - Camera Permissions

    private func handleCurrentAuthorizationStatus() {
        let authorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
        switch authorizationStatus {
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { _ in
                DispatchQueue.main.async { [weak self] in
                    self?.handleCurrentAuthorizationStatus()
                }
            }

        case .restricted, .denied:
            let alert = UIAlertController(model: QRCodeScannerModelFactory.makeCameraDeniedAlert(actionHandler: handle(_:)))
            viewController.present(alert, animated: true, completion: .none)

        case .authorized:
            configureCaptureSessionAndStartRunning()

        @unknown default:
            fatalError("Unknown authorization status: \(authorizationStatus)")
        }
    }

    private func presentCameraUnavailableAlert() {
        let alert = UIAlertController(
            model: QRCodeScannerModelFactory.makeCameraUnavailableAlert(
                actionHandler: handle(_:)
            )
        )
        viewController.present(alert, animated: true, completion: .none)
    }

    // MARK: - Private Methods - Scan Handling

    private func handleScannedCode(with stringValue: String) {
        // Since this method gets called every time a QR code is recognized in a frame from the video feed,
        // and the video capture keeps running continuously, we only want to process and "handle" a QR code if it's
        // "new". We do this by storing the `lastHandledQRCodeString` when it gets processed, and returning straight
        // away if a newly scanned code is the same as the `lastHandledQRCodeString`. This avoids calling the delegate
        // multiple times in a row when scanning a single QR code.
        guard stringValue != lastHandledQRCodeString else {
            return
        }

        onQrCodeScanned(stringValue)
        HapticPlayer.play(.notification(.success))

        lastHandledQRCodeString = stringValue
    }

}

// MARK: -

extension QRCodeScannerPresenterImpl: AVCaptureMetadataOutputObjectsDelegate {

    public func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput avMetadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        /// Verify that the first metadata object is a QR code pointing to a URL.
        guard let object = avMetadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let text = object.stringValue,
              object.type == .qr
        else {
            return
        }
        handleScannedCode(with: text)
    }

}

// MARK: -

private extension AVCaptureSession {

    func removeAllInputs() {
        inputs.forEach { input in
            removeInput(input)
        }
    }

    func removeAllOutputs() {
        outputs.forEach { output in
            removeOutput(output)
        }
    }

}
