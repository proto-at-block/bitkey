import Shared

class DeviceTokenConfigProviderImpl: DeviceTokenConfigProvider {

    private var deviceTokenProvider: DeviceTokenProvider
    private let appVariant: AppVariant

    init(deviceTokenProvider: DeviceTokenProvider, appVariant: AppVariant) {
        self.deviceTokenProvider = deviceTokenProvider
        self.appVariant = appVariant
    }

    func config() async throws -> DeviceTokenConfig? {
        guard let deviceToken = deviceTokenProvider.deviceToken else {
            return nil
        }

        return DeviceTokenConfig(
            deviceToken: deviceToken,
            touchpointPlatform: .from(appVariant: appVariant)
        )
    }
}

enum DeviceTokenError: Error {
    case devicetokenNotFound
}
