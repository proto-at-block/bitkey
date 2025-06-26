import Foundation
import UIKit
import UserNotifications

// MARK: -

public protocol NotificationManager: AnyObject, UNUserNotificationCenterDelegate {
    // Called by the application delegate when the app enters the foreground.
    func applicationDidEnterForeground(_ application: Application)

    // Called by the application delegate when the app successfully registers for notifications
    func application(
        _: Application,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    )

    var delegate: NotificationManagerDelegate? { get set }
}

public protocol NotificationManagerDelegate: AnyObject {
    func receivedNotificationWithInfo(_ info: [AnyHashable: Any])
}
