import Combine
import core
import CoreNFC
import Shared

public class NfcSessionImpl: NSObject, NfcSession {
    public var parameters: NfcSessionParameters

    public var message: String? {
        get { self.delegate?.message }
        set(message) { self.delegate?.message = message }
    }

    fileprivate var delegate: NfcSessionDelegate?

    init(parameters: NfcSessionParameters) throws {
        guard NFCTagReaderSession.readingAvailable else {
            throw NfcException.IOSOnlyNotAvailable().asError()
        }

        // We need to init first before we can reference self when instantiating the delegate
        // object.
        self.parameters = parameters
        self.delegate = nil
        super.init()
        let delegate = NfcSessionDelegate(
            { parameters.onTagConnected() },
            parameters.onTagDisconnected
        )

        if let session = NFCTagReaderSession(pollingOption: .iso14443, delegate: delegate) {
            delegate.setInitialSession(session: session)
            session.alertMessage = parameters.needsAuthentication ? NFCStrings
                .tapInstructions : NFCStrings.tapInstructionsNoAuthNeeded
            session.begin()
        } else {
            throw NfcException.IOSOnlyNoSession(message: nil, cause: nil).asError()
        }

        self.delegate = delegate
    }

    public func transceive(buffer: [KotlinUByte]) async throws -> [KotlinUByte] {
        guard let delegate,
              let apdu = NFCISO7816APDU(data: Data(buffer.map(\.uint8Value)))
        else {
            throw NfcException.IOSOnlyInvalidAPDU().asError()
        }

        for await readiness in delegate.$tag.values {
            switch readiness {
            case .Waiting:
                continue

            case let .Ready(tag):
                var response = Data()
                do {
                    let (data, sw1, sw2) = try await tag.sendCommand(apdu: apdu)
                    response.append(contentsOf: data)
                    response.append(sw1)
                    response.append(sw2)
                } catch {
                    self.delegate?.reconnect()
                    throw NfcException.CanBeRetriedTransceiveFailure(
                        message: error.localizedDescription,
                        cause: nil
                    )
                    .asError()
                }

                return response.map { KotlinUByte(value: $0) }

            case let .Invalidated(error):
                switch error.code {
                case .readerSessionInvalidationErrorSessionTimeout:
                    throw NfcException.Timeout(message: error.localizedDescription, cause: nil)
                        .asError()

                case .readerSessionInvalidationErrorUserCanceled:
                    throw NfcException.IOSOnlyUserCancellation(
                        message: error.localizedDescription,
                        cause: nil
                    )
                    .asError()

                default:
                    throw NfcException.IOSOnlyNoSession(
                        message: error.localizedDescription,
                        cause: nil
                    )
                    .asError()
                }
            }
        }
        fatalError()
    }

    public func close() {
        self.delegate?.close()
    }
}

private class NfcSessionDelegate: NSObject, NFCTagReaderSessionDelegate {
    @Published var tag = SessionReadiness.Waiting
    private let onTagConnected: () -> Void
    private let onTagDisconnected: () -> Void
    private weak var session: NFCTagReaderSession? = nil

    public var message: String? {
        get { self.session?.alertMessage }
        set(message) {
            guard let nfcSession = self.session, let message else { return }
            nfcSession.alertMessage = message
        }
    }

    init(_ onTagConnected: @escaping () -> Void, _ onTagDisconnected: @escaping () -> Void) {
        self.onTagConnected = onTagConnected
        self.onTagDisconnected = onTagDisconnected
    }

    func setInitialSession(session: NFCTagReaderSession) {
        guard self.session == nil else { return }
        self.session = session
    }

    func reconnect() {
        self.tag = .Waiting
        self.session?.restartPolling()
    }

    func close() {
        self.session?.invalidate()
    }

    public func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {
        log(.debug, tag: "NFC") { "Activating NFC session" }
        self.session = session
    }

    public func tagReaderSession(_: NFCTagReaderSession, didInvalidateWithError error: Error) {
        DispatchQueue.main.async {
            FwupNfcMaskOverlayViewController.hide()
        }
        log(.debug, tag: "NFC") { "Invalidating NFC session: \(error)" }
        self.session = nil
        self.tag = .Invalidated(error as! NFCReaderError)

        // Don't send onTagDisconnected for all errors or else we will try to reconnect again when
        // the session
        // was invalidated. TODO: Figure out what situations iOS can be reconnected.
    }

    public func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let tag = tags.first, tags.count == 1 else {
            log(.debug, tag: "NFC") { "Multiple tags (\(tags.count)) detected" }
            session.restartPolling()
            return
        }

        guard case let .iso7816(isoTag) = tag else {
            log(.debug, tag: "NFC") { "An incompatible tag was detected" }
            session.restartPolling()
            return
        }

        session.connect(to: tag, completionHandler: { (error: Error?) in
            if let error {
                log(.debug, tag: "NFC") { "Failed to establish NFC connection: \(error)" }
                session.restartPolling()
            } else {
                log(.debug, tag: "NFC") {
                    "Connecting to tag: \(isoTag.identifier.hexEncodedString())"
                }
                self.tag = .Ready(isoTag)
                self.onTagConnected()
            }
        })
    }
}

private enum SessionReadiness {
    case Waiting
    case Ready(_ tag: NFCISO7816Tag)
    case Invalidated(_ error: NFCReaderError)
}

private extension Data {
    func hexEncodedString() -> String {
        return map { String(format: "%02hhx", $0) }.joined()
    }
}

// TODO: foundation-[mobile]-app-localization-4f75ff34e3da
private enum NFCStrings {
    static let tapInstructions = "Hold your unlocked device to the back of your phone"
    static let tapInstructionsNoAuthNeeded = "Hold your device to the back of your phone"
}
