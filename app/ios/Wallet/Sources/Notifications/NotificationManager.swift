import Foundation
import UIKit
import UserNotifications

// MARK: -

public protocol NotificationManager: AnyObject, UNUserNotificationCenterDelegate {
    // Called by the application delegate when the app enters the foreground.
    func applicationDidEnterForeground(_ application: Application)

    // Called by the application delegate when the app successfully registers for notifcations
    func application(_: Application, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data)
}
