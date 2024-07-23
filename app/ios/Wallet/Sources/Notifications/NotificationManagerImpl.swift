import Foundation
import Shared
import UIKit
import UserNotifications

// MARK: -

public class NotificationManagerImpl: NSObject, NotificationManager {

    // MARK: - Private Properties

    private let appVariant: AppVariant
    private let deviceTokenManager: DeviceTokenManager
    private let deviceTokenProvider: DeviceTokenProvider
    private let eventTracker: EventTracker
    private let notificationCenter: UserNotificationCenter
    private let pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider

    // MARK: - Life Cycle

    public init(
        appVariant: AppVariant,
        deviceTokenManager: DeviceTokenManager,
        deviceTokenProvider: DeviceTokenProvider,
        eventTracker: EventTracker,
        notificationCenter: UserNotificationCenter = UNUserNotificationCenter.current(),
        pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider
    ) {
        self.appVariant = appVariant
        self.deviceTokenManager = deviceTokenManager
        self.deviceTokenProvider = deviceTokenProvider
        self.eventTracker = eventTracker
        self.notificationCenter = notificationCenter
        self.pushNotificationPermissionStatusProvider = pushNotificationPermissionStatusProvider
    }

    // MARK: - Notification Manager

    public func applicationDidEnterForeground(_ application: Application) {
        // Clear any notification badges now that the app has been opened
        if #available(iOS 16.0, *) {
            notificationCenter.setBadgeCount(0, withCompletionHandler: .none)
        } else {
            application.applicationIconBadgeNumber = 0
        }

        // Register for push notifications if authorized
        Task {
            await checkNotificationAuthorizationAndRegisterForPushNotifications()
        }
    }

    public func application(
        _: Application,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        log { "Registered for remote notifications" }

        let decodedDeviceToken = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        Task { @MainActor in
            let result = try? await deviceTokenManager.addDeviceTokenIfActiveOrOnboardingAccount(
                deviceToken: decodedDeviceToken,
                touchpointPlatform: .from(appVariant: appVariant)
            )
            result?.onSuccess(action: { _ in
                self.deviceTokenProvider.setDeviceToken(deviceToken: decodedDeviceToken)
            })
            result?.onFailure(action: { error in
                log(.warn, error: error.asError()) { "Failed to add device token" }
            })
        }
    }

    // MARK: - Private Methods

    private func checkNotificationAuthorizationAndRegisterForPushNotifications() async {
        let settings = await notificationCenter.notificationSettings()
        pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
            status: UNAuthorizationStatusExtensions().convertToNotificationPermissionStatus(
                authorizationStatus: KotlinLong(
                    integerLiteral: settings.authorizationStatus
                        .rawValue
                )
            )
        )

        guard settings.authorizationStatus == .authorized else {
            return
        }

        DispatchQueue.main.async {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    // MARK: - UNUserNotificationCenterDelegate Methods

    @MainActor
    public func userNotificationCenter(
        _: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        switch response.actionIdentifier {
        case UNNotificationDefaultActionIdentifier:
            eventTracker.track(action: .actionAppPushNotificationOpen, context: nil)
        case UNNotificationDismissActionIdentifier:
            eventTracker.track(action: .actionAppPushNotificationDismiss, context: nil)
        default:
            break
        }
    }

    public func userNotificationCenter(
        _: UNUserNotificationCenter,
        willPresent _: UNNotification
    ) async -> UNNotificationPresentationOptions {
        return [.banner, .sound]
    }

}
