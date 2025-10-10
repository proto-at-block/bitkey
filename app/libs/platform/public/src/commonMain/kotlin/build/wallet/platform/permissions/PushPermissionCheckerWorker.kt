package build.wallet.platform.permissions

import build.wallet.worker.AppWorker

/**
 * Worker that refreshes push notification permission status when the app enters foreground.
 *
 * This worker addresses the limitation that push notification permission changes made in
 * system settings are not automatically detected by the app. When users go to iOS Settings
 * or Android Settings and enable/disable push notifications, then return to the app, this
 * worker ensures the permission status is refreshed and reactive flows are updated.
 *
 * ## How it works:
 * - Listens for [AppSessionState.FOREGROUND] events via [AppSessionManager]
 * - On each foreground event, checks current system permission status via [PermissionChecker]
 * - Updates [PushNotificationPermissionStatusProvider] with the latest status
 * - [StateFlow] automatically deduplicates, so downstream flows only react to actual changes
 *
 * @see PushNotificationPermissionStatusProvider
 * @see AppSessionManager
 */
interface PushPermissionCheckerWorker : AppWorker
