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
                buttonIsEnabled: false,
                isKeypad: false,
                isRevamp: false
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
                buttonIsEnabled: true,
                isKeypad: false,
                isRevamp: false
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_limit_picker_zero_with_keypad() {
        let view = SpendingLimitPickerView(
            viewModel: .snapshot(
                primaryAmount: "$0",
                secondaryAmount: "0 sats",
                value: 0,
                buttonIsEnabled: false,
                isKeypad: true,
                isRevamp: true
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_limit_picker_nonzero_with_keypad() {
        let view = SpendingLimitPickerView(
            viewModel: .snapshot(
                primaryAmount: "$150",
                secondaryAmount: "456,789 sats",
                value: 150,
                buttonIsEnabled: true,
                isKeypad: true,
                isRevamp: true
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
        buttonIsEnabled: Bool,
        isKeypad: Bool,
        isRevamp: Bool
    ) -> SpendingLimitPickerModel {
        let maxValue = Float(200.0)
        let entryMode = if isKeypad {
            EntryMode.Keypad(
                amountModel: .init(
                    primaryAmount: primaryAmount,
                    primaryAmountGhostedSubstringRange: nil,
                    secondaryAmount: secondaryAmount,
                    eventTrackerScreenInfo: nil
                ),

                keypadModel: .init(
                    showDecimal: false,
                    onButtonPress: { _ in }
                )
            )
        } else {
            EntryMode.Slider(sliderModel: .init(
                primaryAmount: primaryAmount,
                secondaryAmount: secondaryAmount,
                value: value,
                valueRange: FloatingPointRange(start: 0, endInclusive: 200),
                onValueUpdate: { _ in },
                isEnabled: true
            ))
        }

        return .init(
            onBack: {},
            toolbarModel: .init(
                leadingAccessory: .IconAccessoryCompanion().BackAccessory(onClick: {}),
                middleAccessory: nil,
                trailingAccessory: nil
            ),
            entryMode: entryMode,
            spendingLimitsCopy: SpendingLimitsCopy.Companion().get(isRevampOn: isRevamp),
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

    func lessThanOrEquals(a _: Any, b _: Any) -> Bool {
        return false
    }

    func contains(value _: Any) -> Bool {
        return true
    }

    func isEmpty() -> Bool {
        return false
    }
}
