import Foundation
import Shared
import SwiftUI

// MARK: -

public struct SpendingLimitPickerView: View {

    // MARK: - Private Properties

    private let viewModel: SpendingLimitPickerModel

    // MARK: - Life Cycle

    public init(viewModel: SpendingLimitPickerModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        VStack {
            ToolbarView(viewModel: viewModel.toolbarModel)

            Spacer()
                .frame(height: 16)

            if let headerModel = viewModel.headerModel {
                FormHeaderView(viewModel: headerModel, headlineFont: .title1)
            }

            Spacer()
                .frame(height: 24)

            switch viewModel.entryMode {
            case let sliderEntryMode as EntryMode.Slider:
                AmountSliderView(viewModel: sliderEntryMode.sliderModel)
            case let keypadEntryMode as EntryMode.Keypad:
                VStack {
                    Spacer()

                    AmountEntryView(
                        viewModel: keypadEntryMode.amountModel,
                        disabled: false
                    )

                    Spacer()

                    KeypadView(viewModel: keypadEntryMode.keypadModel)
                        .frame(maxWidth: .infinity, alignment: .center)

                    Spacer()
                        .frame(height: 24)
                }
            default:
                fatalError("Unexpected amount entry mode")
            }

            Spacer()

            ButtonView(model: viewModel.setLimitButtonModel)
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
        .padding(.vertical, DesignSystemMetrics.verticalPadding)
    }

}
