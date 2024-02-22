import DatadogCore
import DatadogLogs
import Foundation
import Shared

public class DatadogLogWriter: Shared.Kermit_coreLogWriter {
    
    private var logWriterContextStore: LogWriterContextStore
    private var minSeverity: Kermit_coreSeverity
    
    private lazy var logger: DatadogLoggerProtocol = {
        let logWriterContext = self.logWriterContextStore.get()
        Datadog.addUserExtraInfo([
            "app_installation_id": logWriterContext.appInstallationId,
            "hardware_serial_number": logWriterContext.hardwareSerialNumber,
        ])
        return DatadogLogger.create(
            with: .init(
                name: "Default",
                networkInfoEnabled: false,
                bundleWithRumEnabled: true,
                bundleWithTraceEnabled: true
            )
        )
    }()
    
    public init(logWriterContextStore: LogWriterContextStore, minSeverity: Kermit_coreSeverity) {
        self.logWriterContextStore = logWriterContextStore
        self.minSeverity = minSeverity
    }
    
    override public func isLoggable(tag: String, severity: Kermit_coreSeverity) -> Bool {
        return severity.compareTo(other: self.minSeverity) >= 0
    }
    
    override public func log(severity: Shared.Kermit_coreSeverity, message: String, tag: String, throwable: Shared.KotlinThrowable?) {
        logger.log(level: severity.asLogLevel(), message: message, error: throwable?.asError(), attributes: ["tag": tag])
    }
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
