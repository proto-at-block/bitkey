import AVFAudio
import Bugsnag
import DatadogCore
import DatadogCrashReporting
import DatadogLogs
import DatadogRUM
import DatadogTrace
import Shared
import SwiftUI
import UIKit
import UserNotifications

// MARK: -

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    // MARK: - Internal Properties

    let appVariant = AppVariant.current()
    var appContext: AppContext!
    var appContextInitializationTask: Task<Void, Never>!

    lazy var window: UIWindow? = UIWindow(frame: UIScreen.main.bounds)

    // MARK: - Life Cycle

    override init() {
        // Initialize crash reporters first.
        initializeBugsnag(appVariant: appVariant)
        initializeDatadog(appVariant: appVariant)

        super.init()
    }

    // MARK: - UIApplicationDelegate

    func application(
        _: UIApplication,
        didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        self.window = window

        appContextInitializationTask = Task.detached { [weak self] in
            guard let appVariant = await self?.appVariant else { return }

            let context = AppContext(appVariant: appVariant, window: window)
            context.appComponent.loggerInitializer.initialize()
            context.appComponent.bugsnagContext.configureCommonMetadata()

            // W-8924: Exclude Library/Application Support directory from iCloud backups. Otherwise, if
            // a user performs an iCloud restore on a new phone, the app will think it is onboarded
            // but
            // will be missing the necessary keychain keys, resulting in a crash on boot. This is
            // considered best-effort, both for our code and per Apple's documentation on
            // isExcludedFromBackup.
            do {
                var url = URL(fileURLWithPath: context.appComponent.fileDirectoryProvider.appDir())
                var resourceValues = URLResourceValues()
                resourceValues.isExcludedFromBackup = true
                try url.setResourceValues(resourceValues)
            } catch {
                log(.error, error: error) { "Failed to exclude appDir from iCloud backup." }
            }

            UNUserNotificationCenter.current().delegate = context.notificationManager
            context.notificationManager.delegate = self

            await MainActor.run { [weak self] in
                self?.appContext = context
                context.sharingManager.mainWindow = window
                window.rootViewController = context.activityComponent.composeAppUIController
                    .viewController
                window.makeKeyAndVisible()

                // We have videos in our app which, even though they don't have audio, means when
                // they play
                // they take over audio control from other apps. This prevents that.
                try? AVAudioSession.sharedInstance().setCategory(
                    .playback,
                    mode: .default,
                    options: [.mixWithOthers]
                )
            }
        }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        appContext.notificationManager.application(
            application,
            didRegisterForRemoteNotificationsWithDeviceToken: deviceToken
        )
    }

    func application(
        _: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        log(.error, error: error) { "Failed to register for remote notifications" }
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        Task {
            await appContextInitializationTask.value
            guard let appContext else { return }
            // Notify the session manager that we are now in the foreground.
            // This is to refresh the session ID if a certain amount of time has passed in the
            // background.
            appContext.appComponent.appSessionManager.appDidEnterForeground()

            // Notify the notification manager that we are now in the foreground so it can perform
            // relevant tasks.
            appContext.notificationManager.applicationDidEnterForeground(application)
        }
    }

    func applicationWillResignActive(_: UIApplication) {
        Task {
            await appContextInitializationTask.value
            guard let appContext else { return }
            if !appContext.appComponent.biometricPrompter.isPrompting,
               !appContext.activityComponent.nfcTransactor.isTransacting
            {
                appContext.appComponent.appSessionManager.appDidEnterBackground()
            }
        }
    }

    func application(
        _: UIApplication,
        continue userActivity: NSUserActivity,
        restorationHandler _: @escaping ([UIUserActivityRestoring]?) -> Void
    ) -> Bool {
        // Get URL components from the incoming user activity.
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let incomingURL = userActivity.webpageURL
        else {
            return false
        }

        if let route = Route.companion.from(url: incomingURL.absoluteString) {
            Router.shared.route = route
            return true
        }

        return false
    }
}

extension AppDelegate: NotificationManagerDelegate {
    func receivedNotificationWithInfo(_ info: [AnyHashable: Any]) {
        if let extras = info as? [String: Any] {
            Router.shared.route = Route.companion.from(extras: extras)
        }
    }
}

// MARK: - Private Methods

private func initializeDatadog(appVariant: AppVariant) {
    let config = DatadogConfig.companion.create(appVariant: appVariant)
    // TODO(W-1144): extract builder configuration to Info.plist
    Datadog.initialize(
        with: .init(
            clientToken: "pubc98a5dc771810d55d228a00baa7c4cdd",
            env: config.environmentName,
            site: .us1
        ),
        trackingConsent: .granted
    )

    // Log Datadog SDK errors in development builds to catch SDK configuration errors.
    if appVariant == AppVariant.development {
        Datadog.verbosityLevel = .warn
    }

    CrashReporting.enable()
    RUM.enable(
        with: RUM.Configuration(
            applicationID: "4a66b458-4cb8-444e-9298-cf9ac3d1f25e",
            uiKitViewsPredicate: nil,
            uiKitActionsPredicate: DefaultUIKitRUMActionsPredicate(),
            urlSessionTracking: RUM.Configuration.URLSessionTracking(
                firstPartyHostsTracing: .trace(
                    hosts: ["wallet.build", "bitkey.build"],
                    sampleRate: 100
                )
            ),
            trackBackgroundEvents: true,
            appHangThreshold: 0.25,
            trackWatchdogTerminations: true,
            telemetrySampleRate: 100
        )
    )
    Trace.enable(
        with: Trace.Configuration(
            sampleRate: 100,
            urlSessionTracking: .init(
                firstPartyHostsTracing: .trace(
                    hosts: ["wallet.build", "bitkey.build"],
                    sampleRate: 100
                )
            ),
            networkInfoEnabled: true
        )
    )
    Logs.enable()
}

private func initializeBugsnag(appVariant: AppVariant) {
    // Get common config
    let config = BugsnagConfig(appVariant: appVariant)
    // Initialize Bugsnag using iOS SDK
    let bugsnagConfig = BugsnagConfiguration.loadConfig()
    bugsnagConfig.appHangThresholdMillis = 500
    bugsnagConfig.enabledBreadcrumbTypes = [.all]
    bugsnagConfig.releaseStage = config.releaseStage
    Bugsnag().initialize(config: bugsnagConfig)
}
