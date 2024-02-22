import Lottie
import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class LoadingSnapshotTests: XCTestCase {

    func test_loading_explicit() {
        let view = LoadingView(viewModel: .init(style: LoadingBodyModelStyleExplicit()))
        assertBitkeySnapshots(view: view)
    }

    func test_loading_explicit_with_message() {
        let view = LoadingView(viewModel: .init(message: "Loading...", style: LoadingBodyModelStyleExplicit()))
        assertBitkeySnapshots(view: view)
    }

    func test_loading_implicit() {
        let view = LoadingView(viewModel: .init(style: LoadingBodyModelStyleImplicit()))
        assertBitkeySnapshots(view: view)
    }

}

// MARK: -

private extension LoadingBodyModel {

    convenience init(
        message: String? = nil,
        style: LoadingBodyModelStyle
    ) {
        self.init(
            message: message,
            onBack: {},
            style: style,
            id: .none,
            eventTrackerScreenIdContext: nil,
            eventTrackerShouldTrack: false
        )
    }

}
