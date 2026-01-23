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

        // Kotlin Int maps to Int32 in Swift, convert to Swift Int
        let maxRetries = Int(parameters.maxNfcRetryAttempts)

        // We need to init first before we can reference self when instantiating the delegate
        // object.
        self.parameters = parameters
        self.delegate = nil
        super.init()
        let delegate = NfcSessionDelegate(
            params: parameters,
            { [weak self] in parameters.onTagConnected(self) },
            parameters.onTagDisconnected,
            maxRetries: maxRetries
        )

        try createAndStartSession(delegate, parameters)

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
    private let params: NfcSessionParameters
    private weak var session: NFCTagReaderSession? = nil

    // Retry mechanism for session invalidation
    private var retryCount = 0
    private let maxRetries: Int
    private let retryableErrorCodes: Set<NFCReaderError.Code> = [
        .readerSessionInvalidationErrorSessionTerminatedUnexpectedly,
    ]

    public var message: String? {
        get { self.session?.alertMessage }
        set(message) {
            guard let nfcSession = self.session, let message else { return }
            nfcSession.alertMessage = message
        }
    }

    init(
        params: NfcSessionParameters,
        _ onTagConnected: @escaping () -> Void,
        _ onTagDisconnected: @escaping () -> Void,
        maxRetries: Int = 3
    ) {
        self.onTagConnected = onTagConnected
        self.onTagDisconnected = onTagDisconnected
        self.params = params
        self.maxRetries = maxRetries
    }

    func setSession(session: NFCTagReaderSession) {
        self.session?.invalidate() // Clean up previous session
        self.session = session
    }

    func reconnect() {
        self.tag = .Waiting
        self.session?.restartPolling()
    }

    func resetRetryCount() {
        self.retryCount = 0
    }

    func close() {
        self.session?.invalidate()
    }

    public func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {
        log(.debug, tag: "NFC") { "Activating NFC session" }
        self.session = session
    }

    public func tagReaderSession(
        _ session: NFCTagReaderSession,
        didInvalidateWithError error: Error
    ) {
        // Ignore callbacks from old sessions during retry
        guard session === self.session else {
            log(.debug, tag: "NFC") { "Ignoring invalidation from old session" }
            return
        }

        log(.debug, tag: "NFC") {
            "NFC session invalidated: \(error), retry count: \(self.retryCount)"
        }

        guard let nfcError = error as? NFCReaderError else {
            // Non-NFC error, finalize immediately
            finalizeInvalidation(with: error)
            return
        }

        // Check if this is a retryable error and we haven't exceeded max retries
        if retryableErrorCodes.contains(nfcError.code), retryCount < maxRetries {
            retryCount += 1
            log(.debug, tag: "NFC") {
                "Attempting to restart NFC session (attempt \(self.retryCount)/\(self.maxRetries))"
            }

            // Reset tag state to waiting
            self.tag = .Waiting

            // Attempt to create a new session
            do {
                try createAndStartSession(self, self.params)
                log(.debug, tag: "NFC") { "Successfully restarted NFC session" }
                return // Success, don't finalize
            } catch {
                log(.debug, tag: "NFC") { "Failed to restart NFC session: \(error)" }
                // Fall through to finalize with original error
            }
        }

        // Either not retryable, exceeded max retries, or restart failed
        if retryCount >= maxRetries {
            log(.debug, tag: "NFC") { "Exceeded maximum retry attempts (\(self.maxRetries))" }
        }

        finalizeInvalidation(with: error)
    }

    private func finalizeInvalidation(with error: Error) {
        self.session = nil
        // NFCReaderError is a domain-specific NSError. If the error isn't already an
        // NFCReaderError,
        // create one with the userCanceled code as a fallback.
        let nfcError: NFCReaderError = (error as? NFCReaderError) ?? NFCReaderError(
            _nsError: NSError(
                domain: NFCErrorDomain,
                code: NFCReaderError.readerSessionInvalidationErrorUserCanceled.rawValue,
                userInfo: [NSLocalizedDescriptionKey: error.localizedDescription]
            )
        )
        self.tag = .Invalidated(nfcError)
        self.resetRetryCount()

        // Don't send onTagDisconnected for all errors or else we will try to reconnect again when
        // the session was invalidated. TODO: Figure out what situations iOS can be reconnected.
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
                // Reset retry count on successful connection
                self.resetRetryCount()
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

private func createAndStartSession(
    _ delegate: NfcSessionDelegate,
    _ parameters: NfcSessionParameters
) throws {
    if let session = NFCTagReaderSession(pollingOption: .iso14443, delegate: delegate) {
        delegate.setSession(session: session)
        session.alertMessage = parameters.needsAuthentication ? NFCStrings
            .tapInstructions : NFCStrings.tapInstructionsNoAuthNeeded
        session.begin()
        log(.debug, tag: "NFC") { "Started new NFC session: \(session)" }
    } else {
        throw NfcException.IOSOnlyNoSession(message: nil, cause: nil).asError()
    }
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
