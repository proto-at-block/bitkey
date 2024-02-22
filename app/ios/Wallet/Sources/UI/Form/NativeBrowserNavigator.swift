import SafariServices
import Shared
import SwiftUI
import UIKit

class NativeBrowserNavigator : BrowserNavigator {

    var openSafariView: (String) -> Void

    init(openSafariView: @escaping (String) -> Void) {
        self.openSafariView = openSafariView
    }

    func open(url: String) {
        openSafariView(url)
    }

}

// MARK: -

struct SafariView: UIViewControllerRepresentable {

    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        return SFSafariViewController(url: url)
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}

}

// MARK: -

extension URL : Identifiable {
    public var id: URL { return self }
}
