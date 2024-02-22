import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class SpendingLimitPickerSnapshotTests: XCTestCase {

    func test_limit_picker_zero() {
        let view = SpendingLimitPickerView(
            viewModel: .snapshot(
                primaryAmount: "$0",
                secondaryAmount: "0 sats",
                value: 0,
                buttonIsEnabled: false
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_limit_picker_nonzero() {
        let view = SpendingLimitPickerView(
            viewModel: .snapshot(
                primaryAmount: "$150",
                secondaryAmount: "456,789 sats",
                value: 150,
                buttonIsEnabled: true
            )
        )

        assertBitkeySnapshots(view: view)
    }

}

// MARK: -

private extension SpendingLimitPickerModel {

    static func snapshot(
        primaryAmount: String,
        secondaryAmount: String,
        value: Float,
        buttonIsEnabled: Bool
    ) -> SpendingLimitPickerModel {
        let maxValue = Float(200.0)
        return .init(
            onBack: {},
            toolbarModel: .init(leadingAccessory: .IconAccessoryCompanion().BackAccessory(onClick: {}), middleAccessory: nil, trailingAccessory: nil),
            limitSliderModel: .init(
                primaryAmount: primaryAmount,
                secondaryAmount: secondaryAmount,
                value: value,
                valueRange: FloatingPointRange(start: 0, endInclusive: 200),
                onValueUpdate: { _ in },
                isEnabled: true
            ),
            setLimitButtonEnabled: buttonIsEnabled,
            setLimitButtonLoading: false,
            onSetLimitClick: {}
        )
    }

}

private class FloatingPointRange: KotlinClosedFloatingPointRange {
    var start: Any
    var endInclusive: Any

    init(start: Float, endInclusive: Float) {
        self.start = KotlinFloat(float: start)
        self.endInclusive = KotlinFloat(float: endInclusive)
    }

    func lessThanOrEquals(a: Any, b: Any) -> Bool {
        return false
    }

    func contains(value: Any) -> Bool {
        return true
    }

    func isEmpty() -> Bool {
        return false
    }
}
