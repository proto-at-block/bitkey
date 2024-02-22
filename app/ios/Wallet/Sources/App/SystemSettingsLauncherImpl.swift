import Shared
import UIKit

class SystemSettingsLauncherImpl: SystemSettingsLauncher {
    func launchSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString),  UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        }
    }
}
