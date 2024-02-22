import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class ActivitySnapshotTests: XCTestCase {
    
    func test_activity() {
        let view = FormView(
            viewModel: ListFormBodyModelKt.ListFormBodyModel(
                onBack: {},
                toolbarTitle: "Activity",
                listGroups: ListModel.transactionsSnapshotTest.sections,
                id: nil
            )
        )
        
        assertBitkeySnapshots(view: view)
    }
    
}
