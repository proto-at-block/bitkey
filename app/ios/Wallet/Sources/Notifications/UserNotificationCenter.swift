import UserNotifications

public protocol UserNotificationCenter {

    @available(iOS 16.0, *)
    func setBadgeCount(
        _ newBadgeCount: Int,
        withCompletionHandler completionHandler: ((Error?) -> Void)?
    )

    func notificationSettings() async -> UNNotificationSettings

}

extension UNUserNotificationCenter: UserNotificationCenter {}
