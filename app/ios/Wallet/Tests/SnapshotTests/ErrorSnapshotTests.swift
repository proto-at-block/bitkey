import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class ErrorSnapshotTests: XCTestCase {

    func test_error_with_subline() {
        let view = FormView(
            viewModel: ErrorFormBodyModelKt.ErrorFormBodyModel(
                title: "Error message",
                subline: "Error description",
                primaryButton: ButtonDataModel(text: "Done", isLoading: false, onClick: {}, leadingIcon: nil),
                onBack: {},
                toolbar: nil,
                secondaryButton: ButtonDataModel(text: "Go Back", isLoading: false, onClick: {}, leadingIcon: nil),
                renderContext: RenderContext.screen,
                eventTrackerScreenId: nil,
                eventTrackerScreenIdContext: nil,
                eventTrackerShouldTrack: false, 
                onLoaded: {},
                secondaryButtonIcon: Icon.smalliconarrowupright
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_error_without_subline() {
        let view = FormView(
            viewModel: ErrorFormBodyModelKt.ErrorFormBodyModel(
                title: "Error message",
                subline: nil,
                primaryButton: ButtonDataModel(text: "Done", isLoading: false, onClick: {}, leadingIcon: nil),
                onBack: {},
                toolbar: nil,
                secondaryButton: nil,
                renderContext: RenderContext.screen,
                eventTrackerScreenId: nil,
                eventTrackerScreenIdContext: nil,
                eventTrackerShouldTrack: false,
                onLoaded: {},
                secondaryButtonIcon: Icon.smalliconarrowupright
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func xtest_error_with_back() {
        let view = FormView(
            viewModel: ErrorFormBodyModelKt.ErrorFormBodyModel(
                title: "Error message",
                subline: nil,
                primaryButton: ButtonDataModel(text: "Done", isLoading: false, onClick: {}, leadingIcon: nil),
                onBack: {},
                toolbar: nil,
                secondaryButton: nil,
                renderContext: RenderContext.screen,
                eventTrackerScreenId: nil,
                eventTrackerScreenIdContext: nil,
                eventTrackerShouldTrack: false,
                onLoaded: {},
                secondaryButtonIcon: Icon.smalliconarrowupright
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
