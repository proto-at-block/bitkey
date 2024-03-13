import Shared
import XCTest

@testable import Wallet

class NotificationManagerImplTests: XCTestCase {

    let deviceToken = (0..<32).reduce(Data(), {$0 + [$1]})
    let decodedDeviceToken = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"

    private let application = ApplicationMock()
    private var appVariant = AppVariant.development
    private let deviceTokenManager = DeviceTokenManagerMock()
    private let deviceTokenProvider = DeviceTokenProviderMock()
    private let eventTracker = EventTrackerMock()
    private let notificationCenter = UserNotificationCenterMock()
    private let pushNotificationPermissionStatusProvider = PushNotificationPermissionStatusProviderMock()

    var manager: NotificationManager {
        return NotificationManagerImpl(
            appVariant: appVariant,
            deviceTokenManager: deviceTokenManager,
            deviceTokenProvider: deviceTokenProvider,
            eventTracker: eventTracker,
            notificationCenter: notificationCenter,
            pushNotificationPermissionStatusProvider: pushNotificationPermissionStatusProvider
        )
    }

    override func setUp() {
        application.applicationIconBadgeNumber = 0
        deviceTokenManager.reset()
        appVariant = .development
        deviceTokenProvider.reset()
        eventTracker.reset()
        pushNotificationPermissionStatusProvider.reset()
    }

    func test_applicationDidEnterForeground_badgeCount() {
        application.applicationIconBadgeNumber = 1
        manager.applicationDidEnterForeground(application)

        if #available(iOS 16.0, *) {
            XCTAssertEqual(notificationCenter.setBadgeCountCalls.count, 1)
            XCTAssertEqual(notificationCenter.setBadgeCountCalls.first, 0)
        } else {
            XCTAssertEqual(application.applicationIconBadgeNumber, 0)
        }
    }

    func test_applicationDidEnterForeground_updatePushNotificationStatus_authorized() {
        notificationCenter.notificationAuthorizationStatusResult = .authorized
        pushNotificationPermissionStatusProvider.updatePushNotificationStatusCallExpectation = expectation(description: "update call")

        manager.applicationDidEnterForeground(application)

        waitForExpectations(timeout: 10)

        let updatePushNotificationStatusCalls = pushNotificationPermissionStatusProvider.updatePushNotificationStatusCalls
        XCTAssertEqual(updatePushNotificationStatusCalls.count, 1)
        XCTAssertEqual(updatePushNotificationStatusCalls.first, .authorized)
    }

    func test_applicationDidEnterForeground_updatePushNotificationStatus_denied() {
        notificationCenter.notificationAuthorizationStatusResult = .denied
        pushNotificationPermissionStatusProvider.updatePushNotificationStatusCallExpectation = expectation(description: "update call")

        manager.applicationDidEnterForeground(application)

        waitForExpectations(timeout: 10)

        let updatePushNotificationStatusCalls = pushNotificationPermissionStatusProvider.updatePushNotificationStatusCalls
        XCTAssertEqual(updatePushNotificationStatusCalls.count, 1)
        XCTAssertEqual(updatePushNotificationStatusCalls.first, .denied)
    }

    func test_applicationDidEnterForeground_updatePushNotificationStatus_notDetermined() {
        notificationCenter.notificationAuthorizationStatusResult = .notDetermined
        pushNotificationPermissionStatusProvider.updatePushNotificationStatusCallExpectation = expectation(description: "update call")

        manager.applicationDidEnterForeground(application)

        waitForExpectations(timeout: 10)

        let updatePushNotificationStatusCalls = pushNotificationPermissionStatusProvider.updatePushNotificationStatusCalls
        XCTAssertEqual(updatePushNotificationStatusCalls.count, 1)
        XCTAssertEqual(updatePushNotificationStatusCalls.first, .notdetermined)
    }

    func test_applicationDidEnterForeground_updatePushNotificationStatus_ephemeral() {
        notificationCenter.notificationAuthorizationStatusResult = .ephemeral
        pushNotificationPermissionStatusProvider.updatePushNotificationStatusCallExpectation = expectation(description: "update call")

        manager.applicationDidEnterForeground(application)

        waitForExpectations(timeout: 10)

        let updatePushNotificationStatusCalls = pushNotificationPermissionStatusProvider.updatePushNotificationStatusCalls
        XCTAssertEqual(updatePushNotificationStatusCalls.count, 1)
        XCTAssertEqual(updatePushNotificationStatusCalls.first, .notdetermined)
    }

    // TODO W-3590: Fix flaky test for updating provisional notification status

    func test_didRegisterForRemoteNotificationsWithDeviceToken_deviceTokenProvider() {
        manager.application(application, didRegisterForRemoteNotificationsWithDeviceToken: deviceToken)
        XCTAssertEqual(deviceTokenProvider.deviceToken, decodedDeviceToken)
    }

    func test_didRegisterForRemoteNotificationsWithDeviceToken_addDeviceTokenService_developmentVariant() {
        appVariant = .development
        deviceTokenManager.addCallExpectation = expectation(description: "add calls")

        manager.application(application, didRegisterForRemoteNotificationsWithDeviceToken: deviceToken)

        waitForExpectations(timeout: 10)

        let addCalls = deviceTokenManager.addDeviceTokenIfActiveAccountCalls
        XCTAssertEqual(addCalls.count, 1)
        XCTAssertEqual(addCalls.first?.deviceToken, decodedDeviceToken)
        XCTAssertEqual(addCalls.first?.touchpointPlatform, .apnsteam)
    }

    func test_didRegisterForRemoteNotificationsWithDeviceToken_addDeviceTokenService_teamVariant() {
        appVariant = .team
        deviceTokenManager.addCallExpectation = expectation(description: "add calls")

        manager.application(application, didRegisterForRemoteNotificationsWithDeviceToken: deviceToken)

        waitForExpectations(timeout: 10)

        let addCalls = deviceTokenManager.addDeviceTokenIfActiveAccountCalls
        XCTAssertEqual(addCalls.count, 1)
        XCTAssertEqual(addCalls.first?.deviceToken, decodedDeviceToken)
        XCTAssertEqual(addCalls.first?.touchpointPlatform, .apnsteam)
    }

    func test_didRegisterForRemoteNotificationsWithDeviceToken_addDeviceTokenService_customerVariant() {
        appVariant = .customer
        deviceTokenManager.addCallExpectation = expectation(description: "add calls")

        manager.application(application, didRegisterForRemoteNotificationsWithDeviceToken: deviceToken)

        waitForExpectations(timeout: 10)

        let addCalls = deviceTokenManager.addDeviceTokenIfActiveAccountCalls
        XCTAssertEqual(addCalls.count, 1)
        XCTAssertEqual(addCalls.first?.deviceToken, decodedDeviceToken)
        XCTAssertEqual(addCalls.first?.touchpointPlatform, .apnscustomer)
    }

}

// MARK: -

private class ApplicationMock : NSObject, Application {
    var applicationIconBadgeNumber: Int = 0
}

// MARK: -

private class DeviceTokenManagerMock : DeviceTokenManager {

    var addCallExpectation: XCTestExpectation?

    var addDeviceTokenIfActiveAccountResult: DeviceTokenManagerResult<KotlinUnit, DeviceTokenManagerError> = DeviceTokenManagerResultOk(value: KotlinUnit())
    var addDeviceTokenIfActiveAccountCalls = [(deviceToken: String, touchpointPlatform: TouchpointPlatform)]()
    func addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken: String,
        touchpointPlatform: TouchpointPlatform
    ) async throws -> DeviceTokenManagerResult<KotlinUnit, DeviceTokenManagerError> {
        addDeviceTokenIfActiveAccountCalls.append((deviceToken, touchpointPlatform))
        addCallExpectation?.fulfill()
        return addDeviceTokenIfActiveAccountResult
    }

    func addDeviceTokenIfPresentForAccount(
        fullAccountId: FullAccountId,
        f8eEnvironment: F8e_publicF8eEnvironment,
        authTokenScope: Auth_publicAuthTokenScope
    ) async throws -> DeviceTokenManagerResult<KotlinUnit, DeviceTokenManagerError> {
        fatalError("Unimplemented")
    }

    func reset() {
        addDeviceTokenIfActiveAccountCalls = []
        addDeviceTokenIfActiveAccountResult = DeviceTokenManagerResultOk(value: KotlinUnit())
    }

}

// MARK: -

fileprivate class DeviceTokenProviderMock : DeviceTokenProvider {

    var deviceToken: String?

    func setDeviceToken(deviceToken: String) {
        self.deviceToken = deviceToken
    }

    func reset() {
        deviceToken = nil
    }

}

// MARK: -

fileprivate class EventTrackerMock : EventTracker {

    public var trackCalls = [Action]()
    func track(action: Action) {
        trackCalls.append(action)
    }

    func track(eventTrackerCountInfo: EventTrackerCountInfo) { fatalError() }

    
    func track(eventTrackerScreenInfo: EventTrackerScreenInfo) { fatalError() }

    func reset() {
        trackCalls = []
    }
}

// MARK: -

fileprivate class UserNotificationCenterMock : UserNotificationCenter {

    class NSCoderMock: NSCoder {
        let authorizationStatus: Int
        init(authorizationStatus: UNAuthorizationStatus) {
            self.authorizationStatus = authorizationStatus.rawValue
        }
        override func decodeInt64(forKey key: String) -> Int64 { return Int64(authorizationStatus) }
        override func decodeBool(forKey key: String) -> Bool { return true }
    }

    var setBadgeCountCalls = [Int]()
    func setBadgeCount(_ newBadgeCount: Int, withCompletionHandler completionHandler: ((Error?) -> Void)?) {
        setBadgeCountCalls.append(newBadgeCount)
    }

    var notificationAuthorizationStatusResult = UNAuthorizationStatus.authorized
    func notificationSettings() async -> UNNotificationSettings {
        return UNNotificationSettings(coder: NSCoderMock(authorizationStatus: notificationAuthorizationStatusResult))!
    }

    func reset() {
        setBadgeCountCalls = []
        notificationAuthorizationStatusResult = .authorized
    }

}

// MARK: -

fileprivate class PushNotificationPermissionStatusProviderMock : PushNotificationPermissionStatusProvider {

    func pushNotificationStatus() -> Kotlinx_coroutines_coreStateFlow { fatalError() }

    var updatePushNotificationStatusCallExpectation: XCTestExpectation?
    public var updatePushNotificationStatusCalls = [PermissionStatus]()
    func updatePushNotificationStatus(status: PermissionStatus) {
        updatePushNotificationStatusCallExpectation?.fulfill()
        updatePushNotificationStatusCalls.append(status)
    }

    func reset() {
        updatePushNotificationStatusCalls = []
    }

}
