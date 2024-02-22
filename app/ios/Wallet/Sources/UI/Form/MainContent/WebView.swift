import Foundation
import Shared
import SwiftUI
import WebKit

// MARK: -

struct WebView: UIViewRepresentable {
    typealias UIViewType = WKWebView

    // MARK: - Private Properties

    private let viewModel: FormMainContentModelWebView
    private let webView: WKWebView

    // MARK: - Life Cycle

    init(viewModel: FormMainContentModelWebView) {
        self.viewModel = viewModel
        webView = WKWebView(frame: .zero, configuration: WKWebViewConfiguration())
    }

    // MARK: - View
    
    func makeUIView(context: Context) -> WKWebView {
        webView
    }
    
    func updateUIView(_ uiView: WKWebView, context: Context) {
        // TODO W-3142 handle nulls
        let url = URL(string: viewModel.url)!
        let request = URLRequest(url: url)
        webView.load(request)
    }

}
