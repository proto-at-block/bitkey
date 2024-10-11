import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class ExportToolsSnapshotTests: XCTestCase {
    func test_export_selection_screen() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateExportToolsSelectionModel(
                onBack: {},
                onExportTransactionHistoryClick: {},
                onExportDescriptorClick: {}
            )
        )
        assertBitkeySnapshots(view: view)
    }
    
    func test_export_descriptor() {
        let view = createExportSheetBodyModel(
            headline: "Export wallet descriptor",
            subline: "Download your Bitkey wallet descriptor.",
            ctaString: "Download XPUB bundle",
            isLoading: false,
            calloutModel: CalloutModel(
                title: nil,
                subtitle: LabelModelStringModel(
                    string: "XPUB bundles contain sensitive privacy data. For tax reporting, use your transaction history."),
                treatment: .warning,
                leadingIcon: nil,
                trailingIcon: nil,
                onClick: nil
            )
        )
        assertBitkeySnapshots(view: view)
    }
    
    func test_export_transaction_history() {
        let view = createExportSheetBodyModel(
            headline: "Export transaction history",
            subline: "Download your Bitkey transaction history.",
            ctaString: "Download .CSV",
            isLoading: false
        )
        assertBitkeySnapshots(view: view)
    }
    
    private func createExportSheetBodyModel(
        headline: String,
        subline: String,
        ctaString: String,
        isLoading: Bool = false,
        calloutModel: CalloutModel? = nil,
        onCtaClicked: @escaping () -> Void = {},
        onClosed: @escaping () -> Void = {}
    ) -> some View {
        return FormView(
            viewModel: SnapshotTestModels.shared.CreateExportSheetBodyModel(
                headline: headline,
                subline: subline,
                ctaString: ctaString,
                isLoading: isLoading,
                cancelString: "Cancel",
                calloutModel: calloutModel,
                onCtaClicked: onCtaClicked,
                onClosed: onClosed
            )
        )
    }
}
