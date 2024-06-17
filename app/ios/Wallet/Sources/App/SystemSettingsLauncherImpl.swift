import Shared
import UIKit

class SystemSettingsLauncherImpl: SystemSettingsLauncher {

    func launchAppSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString),
           UIApplication.shared.canOpenURL(url)
        {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        }
    }

    func launchSecuritySettings() {
        // we can't launch directly to settings so we will just go to app settings
        launchAppSettings()
    }
}
