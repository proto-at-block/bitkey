import Shared
import UIKit

// MARK: -

@MainActor
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    private var connectionTask: Task<Void, Never>?

    func scene(
        _ scene: UIScene,
        willConnectTo _: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene,
              let appDelegate = UIApplication.shared.delegate as? AppDelegate
        else {
            return
        }

        connectionTask = Task { [weak self, weak windowScene] in
            guard let self,
                  let windowScene,
                  let appContext = await appDelegate.appContext()
            else {
                return
            }

            guard windowScene.activationState != .unattached else { return }

            let window = UIWindow(windowScene: windowScene)
            window.rootViewController = appContext.activityComponent.composeAppUIController
                .viewController
            self.window = window
            appDelegate.scenePresentationContext.attach(window: window)
            window.makeKeyAndVisible()

            connectionOptions.userActivities.forEach { appDelegate.handle($0) }
            connectionOptions.urlContexts.forEach { appDelegate.handle(url: $0.url) }
            if let notificationResponse = connectionOptions.notificationResponse {
                await appContext.notificationManager.didReceive(response: notificationResponse)
            }
        }
    }

    func sceneDidBecomeActive(_: UIScene) {
        guard let appDelegate = UIApplication.shared.delegate as? AppDelegate else { return }
        Task {
            guard let appContext = await appDelegate.appContext() else { return }
            appContext.appComponent.appSessionManager.appDidEnterForeground()
            appContext.notificationManager.applicationDidEnterForeground(UIApplication.shared)
        }
    }

    func sceneWillResignActive(_: UIScene) {
        guard let appDelegate = UIApplication.shared.delegate as? AppDelegate else { return }
        Task {
            guard let appContext = await appDelegate.appContext() else { return }
            if !appContext.appComponent.biometricPrompter.isPrompting,
               !appContext.activityComponent.nfcTransactor.isTransacting
            {
                appContext.appComponent.appSessionManager.appDidEnterBackground()
            }
        }
    }

    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        guard let appDelegate = UIApplication.shared.delegate as? AppDelegate else { return }
        appDelegate.handle(userActivity)
    }

    func scene(_: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard let appDelegate = UIApplication.shared.delegate as? AppDelegate else { return }
        URLContexts.forEach { appDelegate.handle(url: $0.url) }
    }

    func sceneDidDisconnect(_: UIScene) {
        connectionTask?.cancel()
        connectionTask = nil

        guard let window,
              let appDelegate = UIApplication.shared.delegate as? AppDelegate
        else {
            return
        }
        appDelegate.scenePresentationContext.detach(window: window)
        self.window = nil
    }
}
