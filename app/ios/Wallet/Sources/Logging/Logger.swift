import Shared

public func log(
    _ level: LogLevel = .info,
    tag: String? = nil,
    error: Error? = nil,
    message: @escaping () -> String
) { LoggerKt.log(level: level, tag: tag, error: error, message: message) }
