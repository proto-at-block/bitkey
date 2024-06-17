import OSLog

// MARK: -

public class LogStore {

    // MARK: - Public Types

    private let subsystemIdentifier = "build.wallet"

    public enum Level: String {
        /// Used for logging informational context.
        case info

        /// Used for logging unexpected failures in the code.
        /// This should not be used for logging expected failures (i.e. network failures, QR
        /// parsing, etc.)
        case error

        case warn

        case debug
    }

    public struct Entry {
        public let level: Level
        public let message: String
        public let date: Date
    }

    // MARK: - Private Properties

    private let logStore: OSLogStore

    // MARK: - Life Cycle

    public init?() {
        guard let store = try? OSLogStore(scope: .currentProcessIdentifier) else {
            return nil
        }

        self.logStore = store
    }

    // MARK: - Public Methods

    public func getEntries() -> [Entry] {
        guard let allEntries = try? logStore
            .getEntries(at: logStore.position(timeIntervalSinceLatestBoot: 0))
        else {
            return []
        }

        return allEntries
            .compactMap { $0 as? OSLogEntryLog }
            .filter { $0.subsystem.contains(subsystemIdentifier) }
            .map { .init(level: $0.level.level, message: $0.composedMessage, date: $0.date) }
    }

}

// MARK: -

private extension OSLogEntryLog.Level {

    var level: LogStore.Level {
        switch self {
        case .notice: return .warn
        case .info: return .info
        case .debug: return .debug
        case .error: return .error
        default: return .info
        }
    }

}
