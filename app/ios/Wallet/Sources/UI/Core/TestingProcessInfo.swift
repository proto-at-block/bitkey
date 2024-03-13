import Foundation

/// Extension to help figure out when snapshot tests are running so we can pause animations
public extension ProcessInfo {
    static var isTesting: Bool {
        processInfo.environment["XCTestConfigurationFilePath"] != nil
    }
}
