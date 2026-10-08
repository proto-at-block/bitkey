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

private let dynamicIslandScannerStatusBarHiddenNotification = Notification.Name(
    "build.wallet.dynamicIslandScannerStatusBarHiddenChanged"
)

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    // MARK: - Internal Properties

    let appVariant = AppVariant.current()
    private(set) var appContext: AppContext?
    private var appContextInitializationTask: Task<AppContext, Never>?
    let scenePresentationContext = ScenePresentationContext()

    // MARK: - Life Cycle

    override init() {
        // Initialize crash reporters first.
        initializeBugsnag(appVariant: appVariant)
        initializeDatadog(appVariant: appVariant)

        super.init()

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleDynamicIslandScannerStatusBarHiddenChanged(_:)),
            name: dynamicIslandScannerStatusBarHiddenNotification,
            object: nil
        )
    }

    // MARK: - UIApplicationDelegate

    func application(
        _: UIApplication,
        didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        appContextInitializationTask = Task.detached { [appVariant, scenePresentationContext] in
            let context = AppContext(
                appVariant: appVariant,
                presentationContext: scenePresentationContext
            )
            context.appComponent.loggerInitializer.initialize()
            context.appComponent.bugsnagContext.configureCommonMetadata()

            // W-8924: Exclude Library/Application Support directory from iCloud backups. Otherwise, if
            // a user performs an iCloud restore on a new phone, the app will think it is onboarded
            // but will be missing the necessary keychain keys, resulting in a crash on boot.
            do {
                var url = URL(fileURLWithPath: context.appComponent.fileDirectoryProvider.appDir())
                var resourceValues = URLResourceValues()
                resourceValues.isExcludedFromBackup = true
                try url.setResourceValues(resourceValues)
            } catch {
                log(.error, error: error) { "Failed to exclude appDir from iCloud backup." }
            }

            await MainActor.run {
                UNUserNotificationCenter.current().delegate = context.notificationManager
                context.notificationManager.delegate = self
                self.appContext = context

                // Prevent silent videos from taking audio control from other apps.
                try? AVAudioSession.sharedInstance().setCategory(
                    .playback,
                    mode: .default,
                    options: [.mixWithOthers]
                )
            }

            return context
        }
        return true
    }

    @MainActor
    func appContext() async -> AppContext? {
        if let appContext {
            return appContext
        }
        return await appContextInitializationTask?.value
    }

    @discardableResult
    func handle(_ userActivity: NSUserActivity) -> Bool {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = userActivity.webpageURL
        else {
            return false
        }
        return handle(url: url)
    }

    @discardableResult
    func handle(url: URL) -> Bool {
        guard let route = Route.companion.from(url: url.absoluteString) else {
            return false
        }
        Router.shared.route = route
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Task { @MainActor in
            guard let appContext = await appContext() else { return }
            appContext.notificationManager.application(
                application,
                didRegisterForRemoteNotificationsWithDeviceToken: deviceToken
            )
        }
    }

    func application(
        _: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        log(.error, error: error) { "Failed to register for remote notifications" }
    }


}

private extension AppDelegate {
    @objc
    func handleDynamicIslandScannerStatusBarHiddenChanged(_ notification: Notification) {
        guard let hidden = notification.userInfo?["hidden"] as? Bool else { return }
        UIApplication.shared.setStatusBarHidden(hidden, with: .fade)
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
            // iOS hang/watchdog parity with Android RumConfiguration.trackNonFatalAnrs(true).
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
