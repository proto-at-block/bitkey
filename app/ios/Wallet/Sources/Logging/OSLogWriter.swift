import OSLog
import Shared

public class OSLogWriter: Kermit_coreLogWriter {

    fileprivate let subsystemIdentifier = "build.wallet"

    private lazy var logger: OSLogger = .init(subsystem: subsystemIdentifier, category: "main")

    override public func log(
        severity: Kermit_coreSeverity,
        message: String,
        tag: String,
        throwable: KotlinThrowable?
    ) {
        logger.log(
            level: severity.asLogLevel(),
            "(\(tag, privacy: .public)) \(message, privacy: .public) \(throwable?.asError().localizedDescription ?? "", privacy: .public)"
        )
    }
}

extension Kermit_coreSeverity {
    func asLogLevel() -> OSLogType {
        switch self {
        case .verbose: return OSLogType.debug
        case .debug: return OSLogType.debug
        case .info: return OSLogType.info
        case .warn: return OSLogType.info
        case .error: return OSLogType.error
        case .assert: return OSLogType.fault
        default: return OSLogType.info
        }
    }
}
