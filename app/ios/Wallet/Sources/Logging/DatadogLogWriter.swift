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

        let strongThrowable = throwable

        let error: Error? = if let strongThrowable {
            strongThrowable.asError()
        } else {
            nil
        }

        var attributes: [String: Encodable] = ["tag": tag]
        if let appSessionId = logContext.appSessionId {
            attributes["app_session_id"] = appSessionId
        }
        getLogger().log(
            level: severity.asLogLevel(),
            message: message,
            error: error,
            attributes: attributes
        )
    }
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
