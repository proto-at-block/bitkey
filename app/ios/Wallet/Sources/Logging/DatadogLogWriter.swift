import DatadogCore
import DatadogLogs
import Foundation
import Shared

public class DatadogLogWriter: Shared.Kermit_coreLogWriter {

    private var logWriterContextStore: LogWriterContextStore
    private var minSeverity: Kermit_coreSeverity

    private let loggerLock = NSLock()
    private var logger: DatadogLoggerProtocol?

    /// Tracks the last user properties pushed to Datadog so we only call addUserExtraInfo
    /// when values actually change (e.g. after pairing sets the hardware serial number).
    private var lastUserProperties: UserProperties?

    private func getLogger() -> DatadogLoggerProtocol {
        loggerLock.lock()
        defer { loggerLock.unlock() }

        if let logger {
            return logger
        }

        let logger = DatadogLogger.create(
            with: .init(
                name: "Default",
                networkInfoEnabled: false,
                bundleWithRumEnabled: true,
                bundleWithTraceEnabled: true
            )
        )
        self.logger = logger
        return logger
    }

    private func refreshUserPropertiesIfNeeded(_ context: LogWriterContext) {
        let current = UserProperties(
            appInstallationId: context.appInstallationId,
            hardwareSerialNumber: context.hardwareSerialNumber,
            firmwareVersion: context.firmwareVersion
        )
        if current != lastUserProperties {
            lastUserProperties = current
            Datadog.addUserExtraInfo([
                "app_installation_id": current.appInstallationId,
                "hardware_serial_number": current.hardwareSerialNumber,
                "firmware_version": current.firmwareVersion,
            ])
        }
    }

    public init(logWriterContextStore: LogWriterContextStore, minSeverity: Kermit_coreSeverity) {
        self.logWriterContextStore = logWriterContextStore
        self.minSeverity = minSeverity
    }

    override public func isLoggable(tag _: String, severity: Kermit_coreSeverity) -> Bool {
        return severity.compareTo(other: self.minSeverity) >= 0
    }

    override public func log(
        severity: Shared.Kermit_coreSeverity,
        message: String,
        tag: String,
        throwable: Shared.KotlinThrowable?
    ) {
        let logContext: LogWriterContext
        loggerLock.lock()
        logContext = logWriterContextStore.get()
        refreshUserPropertiesIfNeeded(logContext)
        loggerLock.unlock()

        // Redact sensitive data (bitcoin keys/addresses/txids, BIP-39 phrases, recovery
        // codes) before uploading, mirroring the Android DatadogLogWriter. The throwable's
        // full cause chain is checked too because Datadog uploads nested exceptions.
        let redaction = DatadogLogWriter.redact(
            tag: tag,
            message: message,
            throwable: throwable
        )

        let error: Error? = if let throwable {
            if redaction.dropThrowable {
                // The throwable's message (or the stack trace embedding it) may contain the
                // sensitive data that triggered redaction. Replace with a synthetic error so
                // error-level semantics are preserved in Datadog.
                RedactedError()
            } else {
                throwable.asError()
            }
        } else {
            nil
        }

        var attributes: [String: Encodable] = ["tag": redaction.tag]
        if let appSessionId = logContext.appSessionId {
            attributes["app_session_id"] = appSessionId
        }
        getLogger().log(
            level: severity.asLogLevel(),
            message: redaction.message,
            error: error,
            attributes: attributes
        )
    }
}

extension DatadogLogWriter {
    struct Redaction: Equatable {
        let tag: String
        let message: String
        /// True when sensitive data was detected and an associated throwable (if any) must
        /// not be uploaded.
        let dropThrowable: Bool
    }

    /// Runs the shared KMP `SensitiveDataValidator` over the log entry (including the
    /// associated throwable's message) and returns the values safe to upload. Static so it
    /// can be unit tested without constructing a writer (which requires the Datadog SDK to
    /// be initialized).
    static func redact(
        tag: String,
        message: String,
        throwable: KotlinThrowable?
    ) -> Redaction {
        let result = SensitiveDataValidator.shared.check(
            entry: LogEntry(tag: tag, message: message),
            throwable: throwable
        )
        switch result {
        case let sensitive as SensitiveDataResultSensitive:
            return Redaction(
                tag: sensitive.redactedTag,
                message: sensitive.redactedMessage,
                dropThrowable: true
            )
        default:
            return Redaction(tag: tag, message: message, dropThrowable: false)
        }
    }
}

/// Replaces a throwable whose message (or the stack trace embedding it) may contain
/// sensitive data. Carries no information from the original.
private struct RedactedError: Error, CustomStringConvertible {
    var description: String { "REDACTED - Possible sensitive data in throwable" }
}

private struct UserProperties: Equatable {
    let appInstallationId: String?
    let hardwareSerialNumber: String?
    let firmwareVersion: String?
}

extension Shared.Kermit_coreSeverity {
    func asLogLevel() -> DatadogLogLevel {
        switch self {
        case .verbose: return DatadogLogLevel.debug
        case .debug: return DatadogLogLevel.debug
        case .info: return DatadogLogLevel.info
        case .warn: return DatadogLogLevel.warn
        case .error: return DatadogLogLevel.error
        case .assert: return DatadogLogLevel.critical
        default: return DatadogLogLevel.info
        }
    }
}
