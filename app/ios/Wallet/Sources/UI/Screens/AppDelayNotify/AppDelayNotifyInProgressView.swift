import Foundation
import Shared
import SwiftUI

// MARK: -

public struct AppDelayNotifyInProgressView: View {

    // MARK: - Private Properties

    private let viewModel: AppDelayNotifyInProgressBodyModel

    // MARK: - Life Cycle

    public init(viewModel: AppDelayNotifyInProgressBodyModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        GeometryReader { reader in
            VStack(alignment: .leading) {
                FormContentView(
                    toolbarModel: viewModel.toolbar,
                    headerModel: viewModel.header,
                    mainContentList: [
                        FormMainContentModel.Spacer(height: 24),
                        viewModel.timerModel,
                    ],
                    renderContext: .screen
                )
                Spacer()
            }
            .background(Color.background)
            .navigationBarHidden(true)
            .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
            .padding(.bottom, DesignSystemMetrics.verticalPadding)
            .padding(.top, reader.safeAreaInsets.top == 0 ? DesignSystemMetrics.verticalPadding : 0)
        }
    }

}
