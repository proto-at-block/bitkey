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

            FormHeaderView(viewModel: viewModel.headerModel, headlineFont: .title1)

            Spacer()
                .frame(height: 24)

            AmountSliderView(viewModel: viewModel.limitSliderModel)

            Spacer()

            ButtonView(model: viewModel.setLimitButtonModel)
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
        .padding(.vertical, DesignSystemMetrics.verticalPadding)
    }

}

