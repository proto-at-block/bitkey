import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class CurrencyListSnapshotTests: XCTestCase {

    func test_currency_list() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateFiatCurrencyListFormModel(
                onClose: {},
                selectedCurrency: .usd,
                currencyList: [.usd, .gbp, .eur],
                onCurrencySelection: { _ in }
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
