import DatadogRUM
import Shared

public final class DatadogRumMonitorImpl: DatadogRumMonitor {
    public func addError(message: String, source: ErrorSource, attributes: [String: String] = [:]) {
        RUMMonitor.shared().addError(
            message: message,
            source: source.rumErrorSource,
            attributes: attributes
        )
    }

    public func addUserAction(type: ActionType, name: String, attributes: [String: String] = [:]) {
        RUMMonitor.shared().addAction(type: type.rumActionType, name: name, attributes: attributes)
    }

    public func startResourceLoading(
        resourceKey: String,
        method: String,
        url: String,
        attributes: [String: String] = [:]
    ) {
        RUMMonitor.shared().startResource(
            resourceKey: resourceKey,
            httpMethod: RUMMethod(rawValue: method) ?? .get,
            urlString: url,
            attributes: attributes
        )
    }

    public func startView(key: String, name: String, attributes: [String: String] = [:]) {
        RUMMonitor.shared().startView(key: key, name: name, attributes: attributes)
    }

    public func stopResourceLoading(
        resourceKey: String,
        kind: ResourceType,
        attributes: [String: String] = [:]
    ) {
        RUMMonitor.shared().stopResource(
            resourceKey: resourceKey,
            statusCode: nil,
            kind: kind.rumResourceKind,
            attributes: attributes
        )
    }

    public func stopResourceLoadingError(
        resourceKey: String,
        source _: ErrorSource,
        cause: KotlinThrowable,
        attributes: [String: String] = [:]
    ) {
        RUMMonitor.shared().stopResourceWithError(
            resourceKey: resourceKey,
            error: cause.asError(),
            attributes: attributes
        )
    }

    public func stopView(key: String, attributes: [String: String] = [:]) {
        RUMMonitor.shared().stopView(key: key, attributes: attributes)
    }
}

extension ErrorSource {
    var rumErrorSource: RUMErrorSource {
        switch self {
        case ErrorSource.network: return .network
        case ErrorSource.source: return .source
        case ErrorSource.console: return .console
        case ErrorSource.logger: return .custom
        case ErrorSource.agent: return .custom
        case ErrorSource.webview: return .webview
        default: return .custom
        }
    }
}

extension ActionType {
    var rumActionType: RUMActionType {
        switch self {
        case ActionType.tap: return .tap
        case ActionType.scroll: return .scroll
        case ActionType.swipe: return .swipe
        case ActionType.click: return .click
        case ActionType.back: return .custom
        case ActionType.custom: return .custom
        default: return .custom
        }
    }
}

extension ResourceType {
    var rumResourceKind: RUMResourceType {
        return .other
    }
}
