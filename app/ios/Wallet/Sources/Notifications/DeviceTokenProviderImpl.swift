// MARK: -

public class DeviceTokenProviderImpl: DeviceTokenProvider {
    public private(set) var deviceToken: String?

    public init() {
        self.deviceToken = nil
    }

    public func setDeviceToken(deviceToken: String) {
        self.deviceToken = deviceToken
    }
}
